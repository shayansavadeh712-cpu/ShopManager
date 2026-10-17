package com.shopmanager.app

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

object NotifyPrefs {
    fun sp(ctx: Context) = ctx.getSharedPreferences("notif", Context.MODE_PRIVATE)
    fun enabled(ctx: Context) = sp(ctx).getBoolean("on", false)
    fun setEnabled(ctx: Context, v: Boolean) = sp(ctx).edit().putBoolean("on", v).apply()
    fun threshold(ctx: Context) = sp(ctx).getInt("th", 5)
    fun setThreshold(ctx: Context, v: Int) = sp(ctx).edit().putInt("th", v.coerceIn(1, 1000)).apply()
    fun sound(ctx: Context, kind: String) = sp(ctx).getString("snd_$kind", "") ?: ""
    fun setSound(ctx: Context, kind: String, v: String) = sp(ctx).edit().putString("snd_$kind", v).apply()
}

object Notifier {
    private fun kindName(kind: String) = when (kind) {
        "low" -> "کم شدن موجودی"
        "out" -> "تمام شدن موجودی"
        else -> "محصول جدید"
    }

    fun hasPermission(ctx: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** برای هر صدا یک کانال جدا ساخته می‌شود، چون صدای کانال بعد از ساخت قابل تغییر نیست. */
    private fun channelFor(ctx: Context, kind: String): String {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val snd = NotifyPrefs.sound(ctx, kind)
        val id = "sm_${kind}_${snd.hashCode()}"
        if (nm.getNotificationChannel(id) == null) {
            nm.notificationChannels.filter { it.id.startsWith("sm_${kind}_") }.forEach { nm.deleteNotificationChannel(it.id) }
            val ch = NotificationChannel(id, kindName(kind), NotificationManager.IMPORTANCE_HIGH)
            if (snd.isNotBlank()) {
                ch.setSound(
                    Uri.parse(snd),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
            }
            nm.createNotificationChannel(ch)
        }
        return id
    }

    fun notify(ctx: Context, kind: String, key: Int, title: String, text: String) {
        if (!hasPermission(ctx)) return
        val open = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, channelFor(ctx, kind))
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(open)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(kind.hashCode() * 31 + key, n)
    }

    fun test(ctx: Context) {
        notify(ctx, "low", 1, "تست اعلان", "اگر این را می‌بینی و صدا پخش شد، اعلان‌ها درست کار می‌کنند.")
    }

    /** فایل صوتی انتخابی را در پوشهٔ Notifications گوشی ذخیره می‌کند تا سیستم بتواند پخشش کند. */
    fun importSound(ctx: Context, uri: Uri): String? {
        return try {
            if (Build.VERSION.SDK_INT < 29) return uri.toString()
            val mime = ctx.contentResolver.getType(uri) ?: "audio/mpeg"
            val ext = when {
                mime.contains("ogg") -> "ogg"
                mime.contains("wav") -> "wav"
                mime.contains("mp4") || mime.contains("aac") || mime.contains("m4a") -> "m4a"
                else -> "mp3"
            }
            val name = "shop_${System.currentTimeMillis()}.$ext"
            val v = ContentValues()
            v.put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            v.put(MediaStore.Audio.Media.MIME_TYPE, mime)
            v.put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS)
            v.put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            val dest = ctx.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v) ?: return null
            ctx.contentResolver.openInputStream(uri)?.use { i ->
                ctx.contentResolver.openOutputStream(dest)?.use { o -> i.copyTo(o) }
            }
            dest.toString()
        } catch (e: Exception) {
            null
        }
    }

    fun gmtToMillis(s: String): Long = try {
        LocalDateTime.parse(s.take(19)).toInstant(ZoneOffset.UTC).toEpochMilli()
    } catch (e: Exception) {
        0L
    }

    private fun alarmIntent(ctx: Context, id: Long, name: String?): PendingIntent {
        val i = Intent(ctx, PublishReceiver::class.java).putExtra("id", id).putExtra("name", name ?: "")
        return PendingIntent.getBroadcast(
            ctx, id.toInt(), i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    fun scheduleAlarm(ctx: Context, id: Long, name: String, atMillis: Long) {
        if (atMillis <= System.currentTimeMillis()) return
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val pi = alarmIntent(ctx, id, name)
            if (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pi)
            }
        } catch (e: Exception) {
        }
    }

    fun cancelAlarm(ctx: Context, id: Long) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            am.cancel(alarmIntent(ctx, id, null))
        } catch (e: Exception) {
        }
    }

    /** اعلان محصول جدید فقط یک‌بار برای هر محصول. */
    fun notifyNewOnce(ctx: Context, id: Long, name: String) {
        if (!NotifyPrefs.enabled(ctx)) return
        val sp = NotifyPrefs.sp(ctx)
        val done = HashSet(sp.getStringSet("new_done", emptySet()) ?: emptySet())
        if (!done.add(id.toString())) return
        sp.edit().putStringSet("new_done", done).apply()
        notify(ctx, "new", id.toInt(), "محصول جدید منتشر شد", name.ifBlank { "محصول #$id" })
    }

    fun setup(ctx: Context) {
        val wm = WorkManager.getInstance(ctx)
        if (!NotifyPrefs.enabled(ctx)) {
            wm.cancelUniqueWork("stock-check")
            return
        }
        val cons = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
        wm.enqueueUniquePeriodicWork(
            "stock-check", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<StockWorker>(15, TimeUnit.MINUTES).setConstraints(cons).build()
        )
        wm.enqueue(OneTimeWorkRequestBuilder<StockWorker>().setConstraints(cons).build())
    }
}

class PublishReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra("id", 0L)
        if (id == 0L) return
        Notifier.notifyNewOnce(context, id, intent.getStringExtra("name") ?: "")
    }
}

class StockWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val ctx = applicationContext
        if (!NotifyPrefs.enabled(ctx)) return Result.success()
        val wsp = ctx.getSharedPreferences("woo", Context.MODE_PRIVATE)
        val url = wsp.getString("url", "") ?: ""
        val k = Vault.dec(wsp.getString("k", "") ?: "")
        val s = Vault.dec(wsp.getString("s", "") ?: "")
        if (url.isBlank() || k.isBlank() || s.isBlank()) return Result.success()
        val c = WooClient(url, k, s)
        return try {
            check(ctx, c)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }

    private suspend fun fetch(c: WooClient, q: Map<String, String>, maxPages: Int): List<WProduct> {
        val out = ArrayList<WProduct>()
        var pg = 1
        while (pg <= maxPages) {
            val m = LinkedHashMap<String, String>(q)
            m["per_page"] = "100"
            m["page"] = pg.toString()
            val r = c.call("GET", "/products", m)
            if (!r.ok) throw WooException(r.errorText())
            val arr = JSONArray(r.body)
            for (i in 0 until arr.length()) out.add(parseProduct(arr.getJSONObject(i)))
            if (pg >= r.pages) break
            pg++
        }
        return out
    }

    private suspend fun check(ctx: Context, c: WooClient) {
        val sp = NotifyPrefs.sp(ctx)
        val th = NotifyPrefs.threshold(ctx)

        // کم شدن موجودی
        val low = fetch(c, mapOf("status" to "publish", "stock_status" to "instock"), 5)
            .filter { it.manageStock && it.stockQty != null && it.stockQty < th }
        val prevLow = sp.getStringSet("low_ids", emptySet()) ?: emptySet()
        low.filter { !prevLow.contains(it.id.toString()) }.forEach {
            Notifier.notify(ctx, "low", it.id.toInt(), "موجودی رو به اتمام", "«${it.name}» فقط ${fa(it.stockQty ?: 0)} عدد مانده")
        }
        sp.edit().putStringSet("low_ids", low.map { it.id.toString() }.toSet()).apply()

        // تمام شدن موجودی
        val out = fetch(c, mapOf("status" to "publish", "stock_status" to "outofstock"), 3)
        val prevOut = sp.getStringSet("out_ids", emptySet()) ?: emptySet()
        // بار اول فقط وضعیت فعلی ثبت می‌شود تا اعلان انبوه نیاید
        val firstRun = !sp.contains("out_ids")
        if (!firstRun) {
            out.filter { !prevOut.contains(it.id.toString()) }.forEach {
                Notifier.notify(ctx, "out", it.id.toInt(), "موجودی تمام شد", "«${it.name}» ناموجود شد")
            }
        }
        sp.edit().putStringSet("out_ids", out.map { it.id.toString() }.toSet()).apply()

        // محصولات زمان‌بندی‌شده
        val fut = fetch(c, mapOf("status" to "future"), 2)
        fut.forEach { Notifier.scheduleAlarm(ctx, it.id, it.name, Notifier.gmtToMillis(it.dateGmt)) }
        val prevFut = sp.getStringSet("fut_ids", emptySet()) ?: emptySet()
        val nowIds = fut.map { it.id.toString() }.toSet()
        for (id in prevFut) {
            if (nowIds.contains(id)) continue
            val r = c.call("GET", "/products/$id")
            if (r.ok) {
                val p = parseProduct(org.json.JSONObject(r.body))
                if (p.status == "publish") Notifier.notifyNewOnce(ctx, p.id, p.name)
            }
        }
        sp.edit().putStringSet("fut_ids", nowIds).apply()
    }
}

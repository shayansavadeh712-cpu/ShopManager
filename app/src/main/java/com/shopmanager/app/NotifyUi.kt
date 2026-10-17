package com.shopmanager.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val kinds = listOf(
    "low" to "کم شدن موجودی",
    "out" to "تمام شدن موجودی",
    "new" to "انتشار محصول جدید"
)

@Composable
fun NotificationsCard() {
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var on by remember { mutableStateOf(NotifyPrefs.enabled(ctx)) }
    var th by remember { mutableStateOf(NotifyPrefs.threshold(ctx).toString()) }
    var tick by remember { mutableStateOf(0) }
    var pickKind by remember { mutableStateOf("") }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (!ok) Toast.makeText(ctx, "اجازهٔ اعلان داده نشد؛ از تنظیمات گوشی فعالش کن", Toast.LENGTH_LONG).show()
    }
    val ringLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK && pickKind.isNotEmpty()) {
            @Suppress("DEPRECATION")
            val u = r.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            NotifyPrefs.setSound(ctx, pickKind, u?.toString() ?: "")
            tick++
        }
    }
    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null && pickKind.isNotEmpty()) {
            val s = Notifier.importSound(ctx, uri)
            if (s != null) {
                NotifyPrefs.setSound(ctx, pickKind, s)
                tick++
            } else Toast.makeText(ctx, "این فایل صوتی قابل استفاده نبود", Toast.LENGTH_SHORT).show()
        }
    }

    AppCard {
        SectionTitle("اعلان‌ها", Icons.Rounded.Notifications)
        Text(
            "اعلان موجودی کم، تمام‌شدن موجودی و انتشار محصول زمان‌بندی‌شده. بررسی موجودی هر ۱۵ دقیقه در پس‌زمینه انجام می‌شود.",
            fontSize = 12.sp, color = cs.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("فعال", Modifier.weight(1f), fontWeight = FontWeight.SemiBold)
            Switch(on, {
                on = it
                NotifyPrefs.setEnabled(ctx, it)
                if (it && Build.VERSION.SDK_INT >= 33 && !Notifier.hasPermission(ctx)) {
                    permLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
                Notifier.setup(ctx)
            })
        }
        if (on) {
            Spacer(Modifier.height(8.dp))
            Field(th, {
                th = it
                en(it).trim().toIntOrNull()?.let { v -> NotifyPrefs.setThreshold(ctx, v) }
            }, "اعلان وقتی موجودی کمتر از این عدد شد", number = true)
            kinds.forEach { (k, label) ->
                val snd = remember(tick) { NotifyPrefs.sound(ctx, k) }
                Spacer(Modifier.height(10.dp))
                Text(label, fontWeight = FontWeight.Bold)
                Text(
                    "صدا: " + if (snd.isBlank()) "پیش‌فرض گوشی" else "سفارشی",
                    fontSize = 12.sp, color = cs.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ActionButton("صدای سیستم", {
                        pickKind = k
                        val i = Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_NOTIFICATION)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                            .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, true)
                        if (snd.isNotBlank()) i.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, Uri.parse(snd))
                        ringLauncher.launch(i)
                    }, Modifier.weight(1f), kind = BtnKind.TONAL)
                    ActionButton("از فایل", { pickKind = k; fileLauncher.launch("audio/*") }, Modifier.weight(1f), icon = Icons.Rounded.AudioFile, kind = BtnKind.TONAL)
                    ActionButton("پیش‌فرض", { NotifyPrefs.setSound(ctx, k, ""); tick++ }, kind = BtnKind.GHOST)
                }
            }
            Spacer(Modifier.height(12.dp))
            ActionButton("ارسال اعلان آزمایشی", { Notifier.test(ctx) }, Modifier.fillMaxWidth(), icon = Icons.Rounded.NotificationsActive)
        }
    }
}

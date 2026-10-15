package com.shopmanager.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Base64
import androidx.compose.runtime.*
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

class Pref<T : Any>(
    private val sp: SharedPreferences,
    private val key: String,
    private val def: T
) {
    var value by mutableStateOf(read())
        private set

    @Suppress("UNCHECKED_CAST")
    private fun read(): T = when (def) {
        is Int -> sp.getInt(key, def)
        is Float -> sp.getFloat(key, def)
        is Boolean -> sp.getBoolean(key, def)
        is String -> sp.getString(key, def) ?: def
        else -> def
    } as T

    fun set(v: T) {
        value = v
        val e = sp.edit()
        when (v) {
            is Int -> e.putInt(key, v)
            is Float -> e.putFloat(key, v)
            is Boolean -> e.putBoolean(key, v)
            is String -> e.putString(key, v)
            else -> {}
        }
        e.apply()
    }
}

class AppSettings(ctx: Context) {
    private val sp = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    val hue = Pref(sp, "hue2", 14f)
    val themeMode = Pref(sp, "mode2", 2) // 0 سیستم، 1 روشن، 2 تیره، 3 آمولد
    val radius = Pref(sp, "radius2", 14)
    val fontScale = Pref(sp, "fs", 1f)
    val cardStyle = Pref(sp, "card2", 1) // 0 شیشه‌ای، 1 توپر، 2 خطی
    val gradientBg = Pref(sp, "grad2", false)
    val rtl = Pref(sp, "rtl", true)
    val clockStyle = Pref(sp, "clock2", 1)
    val showClock = Pref(sp, "s_clock", true)
    val showQuick = Pref(sp, "s_quick", true)
    val showStats = Pref(sp, "s_stats", true)
    val showHabits = Pref(sp, "s_habits", true)
    val showFinance = Pref(sp, "s_fin", true)
    val showTasks = Pref(sp, "s_tasks", true)
    val showProjects = Pref(sp, "s_proj", true)
}

class AuthStore(ctx: Context) {
    private val sp = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE)

    var loggedIn by mutableStateOf(sp.getBoolean("in", false))
        private set
    var hasAccount by mutableStateOf(sp.contains("hash"))
        private set
    var name by mutableStateOf(sp.getString("name", "") ?: "")
        private set
    var username by mutableStateOf(sp.getString("user", "") ?: "")
        private set

    private var fails = 0
    private var lockUntil = 0L

    private fun hash(pass: String, salt: ByteArray): String {
        val spec = PBEKeySpec(pass.toCharArray(), salt, 60000, 256)
        val b = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        return Base64.encodeToString(b, Base64.NO_WRAP)
    }

    fun register(displayName: String, user: String, pass: String): String? {
        if (displayName.isBlank()) return "نامت را وارد کن"
        if (user.trim().length < 3) return "نام کاربری حداقل ۳ حرف باشد"
        if (pass.length < 6) return "رمز عبور حداقل ۶ کاراکتر باشد"
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val u = user.trim().lowercase()
        sp.edit()
            .putString("name", displayName.trim())
            .putString("user", u)
            .putString("salt", Base64.encodeToString(salt, Base64.NO_WRAP))
            .putString("hash", hash(pass, salt))
            .putBoolean("in", true)
            .apply()
        name = displayName.trim()
        username = u
        hasAccount = true
        loggedIn = true
        return null
    }

    fun login(user: String, pass: String): String? {
        val now = System.currentTimeMillis()
        if (now < lockUntil) return "تلاش ناموفق زیاد بود؛ ${(lockUntil - now) / 1000 + 1} ثانیه صبر کن"
        val salt = Base64.decode(sp.getString("salt", "") ?: "", Base64.NO_WRAP)
        val ok = user.trim().lowercase() == sp.getString("user", "") &&
                hash(pass, salt) == sp.getString("hash", "")
        if (!ok) {
            fails++
            if (fails >= 5) {
                fails = 0
                lockUntil = now + 30_000
            }
            return "نام کاربری یا رمز عبور اشتباه است"
        }
        fails = 0
        sp.edit().putBoolean("in", true).apply()
        loggedIn = true
        return null
    }

    fun changePassword(old: String, new: String): String? {
        val salt = Base64.decode(sp.getString("salt", "") ?: "", Base64.NO_WRAP)
        if (hash(old, salt) != sp.getString("hash", "")) return "رمز فعلی اشتباه است"
        if (new.length < 6) return "رمز جدید حداقل ۶ کاراکتر باشد"
        val ns = ByteArray(16).also { SecureRandom().nextBytes(it) }
        sp.edit()
            .putString("salt", Base64.encodeToString(ns, Base64.NO_WRAP))
            .putString("hash", hash(new, ns))
            .apply()
        return null
    }

    fun logout() {
        sp.edit().putBoolean("in", false).apply()
        loggedIn = false
    }

    fun deleteAccount() {
        sp.edit().clear().apply()
        loggedIn = false
        hasAccount = false
        name = ""
        username = ""
    }
}

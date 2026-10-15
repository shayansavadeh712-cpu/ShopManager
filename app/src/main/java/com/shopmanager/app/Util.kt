package com.shopmanager.app

import android.icu.util.Calendar
import android.icu.util.ULocale
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

val faMonths = listOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند"
)
val faWeek = listOf("شنبه", "یکشنبه", "دوشنبه", "سه‌شنبه", "چهارشنبه", "پنجشنبه", "جمعه")
val faWeekShort = listOf("ش", "ی", "د", "س", "چ", "پ", "ج")

fun fa(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
        when (c) {
            in '0'..'9' -> sb.append('۰' + (c - '0'))
            ',' -> sb.append('٬')
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

fun fa(n: Int): String = fa(n.toString())
fun fa(n: Long): String = fa(n.toString())

fun en(s: String): String {
    val sb = StringBuilder()
    for (c in s) {
        when (c) {
            in '۰'..'۹' -> sb.append('0' + (c - '۰'))
            in '٠'..'٩' -> sb.append('0' + (c - '٠'))
            '٫' -> sb.append('.')
            '٬', ',' -> {}
            else -> sb.append(c)
        }
    }
    return sb.toString()
}

private fun zone(): ZoneId = ZoneId.systemDefault()

fun today(): Long = LocalDate.now().toEpochDay()

fun pc(day: Long): Calendar {
    val c = Calendar.getInstance(ULocale("fa_IR@calendar=persian"))
    c.timeInMillis = LocalDate.ofEpochDay(day).atStartOfDay(zone()).toInstant().toEpochMilli()
    return c
}

fun dayOf(c: Calendar): Long =
    Instant.ofEpochMilli(c.timeInMillis).atZone(zone()).toLocalDate().toEpochDay()

fun monthBase(offset: Int): Calendar {
    val c = pc(today())
    c.set(Calendar.DAY_OF_MONTH, 1)
    c.add(Calendar.MONTH, offset)
    return c
}

fun monthTitle(offset: Int): String {
    val b = monthBase(offset)
    return "${faMonths[b.get(Calendar.MONTH)]} ${fa(b.get(Calendar.YEAR))}"
}

fun weekIdx(d: Long): Int = (LocalDate.ofEpochDay(d).dayOfWeek.value + 1) % 7

fun fmtDay(d: Long): String {
    val c = pc(d)
    return fa("${c.get(Calendar.YEAR)}/${c.get(Calendar.MONTH) + 1}/${c.get(Calendar.DAY_OF_MONTH)}")
}

fun longDay(d: Long): String {
    val c = pc(d)
    return "${faWeek[weekIdx(d)]} ${fa(c.get(Calendar.DAY_OF_MONTH))} ${faMonths[c.get(Calendar.MONTH)]}"
}

fun monthKey(d: Long): Int {
    val c = pc(d)
    return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
}

fun fmtNum(v: Double): String =
    if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.1f", v)

fun money(v: Double): String = fa(String.format(Locale.US, "%,.0f", v))

fun mmss(sec: Int): String = String.format(Locale.US, "%02d:%02d", sec / 60, sec % 60)

fun streak(days: Set<Long>?): Int {
    if (days == null) return 0
    var d = today()
    if (!days.contains(d)) d--
    var n = 0
    while (days.contains(d)) {
        n++
        d--
    }
    return n
}

fun greeting(): String {
    val h = LocalTime.now().hour
    return when {
        h < 5 -> "شب بخیر"
        h < 12 -> "صبح بخیر"
        h < 17 -> "ظهر بخیر"
        h < 20 -> "عصر بخیر"
        else -> "شب بخیر"
    }
}

package com.rooznegar.app

import android.icu.text.SimpleDateFormat
import android.icu.util.Calendar
import android.icu.util.TimeZone
import android.icu.util.ULocale
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

val TEHRAN: ZoneId = ZoneId.of("Asia/Tehran")
fun todayTehran(): LocalDate = LocalDate.now(TEHRAN)

private val faLoc = ULocale("fa_IR@calendar=persian")
private val utc: TimeZone = TimeZone.getTimeZone("UTC")

private fun toDate(d: LocalDate): Date =
    Date.from(d.atTime(12, 0).toInstant(ZoneOffset.UTC))

private fun fmt(d: LocalDate, pattern: String): String {
    val f = SimpleDateFormat(pattern, faLoc)
    f.timeZone = utc
    return f.format(toDate(d))
}

fun jalaliFull(d: LocalDate) = fmt(d, "d MMMM y")
fun jalaliMonthYear(d: LocalDate) = fmt(d, "MMMM y")
fun jalaliDayMonth(d: LocalDate) = fmt(d, "d MMMM")
fun jalaliDay(d: LocalDate) = fmt(d, "d")
fun gregorian(d: LocalDate): String =
    d.format(DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH))

fun faNum(n: Int): String = NumberFormat.getInstance(Locale("fa", "IR")).format(n)

/** Returns (year, month 0-based, dayOfMonth) in the Persian calendar. */
fun jalaliParts(d: LocalDate): Triple<Int, Int, Int> {
    val c = Calendar.getInstance(faLoc)
    c.timeZone = utc
    c.time = toDate(d)
    return Triple(c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH))
}

fun jalaliMonthDays(anyDay: LocalDate): List<LocalDate> {
    val start = anyDay.minusDays((jalaliParts(anyDay).third - 1).toLong())
    val month = jalaliParts(start).second
    val out = mutableListOf<LocalDate>()
    var d = start
    while (jalaliParts(d).second == month) {
        out.add(d)
        d = d.plusDays(1)
    }
    return out
}

fun faTime(h: Int, m: Int): String {
    val f = NumberFormat.getInstance(Locale("fa", "IR"))
    f.minimumIntegerDigits = 2
    return f.format(h) + ":" + f.format(m)
}

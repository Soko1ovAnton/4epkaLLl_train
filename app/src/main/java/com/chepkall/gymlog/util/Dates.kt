package com.chepkall.gymlog.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Dates {

    fun todayEpochDay(): Long = LocalDate.now().toEpochDay()

    fun epochDayToLocal(epochDay: Long): LocalDate = LocalDate.ofEpochDay(epochDay)

    private val monthsFull = arrayOf(
        "января", "февраля", "марта", "апреля", "мая", "июня",
        "июля", "августа", "сентября", "октября", "ноября", "декабря",
    )
    private val monthsNom = arrayOf(
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    private val weekdaysShort = arrayOf("пн", "вт", "ср", "чт", "пт", "сб", "вс")

    /** «5 мая» */
    fun dayMonth(epochDay: Long): String {
        val d = epochDayToLocal(epochDay)
        return "${d.dayOfMonth} ${monthsFull[d.monthValue - 1]}"
    }

    /** «5 мая 2025» */
    fun dayMonthYear(epochDay: Long): String {
        val d = epochDayToLocal(epochDay)
        return "${d.dayOfMonth} ${monthsFull[d.monthValue - 1]} ${d.year}"
    }

    /** «Май 2025» */
    fun monthYear(localDate: LocalDate): String =
        "${monthsNom[localDate.monthValue - 1]} ${localDate.year}"

    /** пн..вс */
    fun weekdayShort(epochDay: Long): String {
        val d = epochDayToLocal(epochDay)
        val idx = (d.dayOfWeek.value + 6) % 7 // Monday=0
        return weekdaysShort[idx]
    }

    fun isToday(epochDay: Long): Boolean = epochDay == todayEpochDay()

    fun relativeLabel(epochDay: Long): String {
        val today = todayEpochDay()
        return when (epochDay) {
            today -> "Сегодня"
            today - 1 -> "Вчера"
            today + 1 -> "Завтра"
            else -> "${weekdayShort(epochDay)}, ${dayMonth(epochDay)}"
        }
    }

    fun monthGrid(firstDay: LocalDate): List<LocalDate?> {
        val firstMonday = firstDay.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val cells = mutableListOf<LocalDate?>()
        var d = firstMonday
        while (!d.isAfter(firstDay.withDayOfMonth(firstDay.lengthOfMonth()))) {
            cells += d
            d = d.plusDays(1)
        }
        while (cells.size % 7 != 0) cells += null
        return cells
    }

    fun timeLabel(epochMillis: Long): String =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(java.time.ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))

    fun durationLabel(seconds: Long): String {
        val h = seconds / 3600
        val m = (seconds % 3600) / 60
        val s = seconds % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.ROOT, "%02d:%02d", m, s)
    }
}
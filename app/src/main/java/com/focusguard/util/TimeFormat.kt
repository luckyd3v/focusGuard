package com.focusguard.util

import java.time.DayOfWeek
import java.util.Locale

object TimeFormat {
    fun minuteOfDay(minutes: Int): String =
        String.format(Locale.ROOT, "%02d:%02d", (minutes / 60) % 24, minutes % 60)

    /** Ex.: "2h 30min", "12 min", "40s". */
    fun duration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        val totalMinutes = totalSeconds / 60
        val hours = totalMinutes / 60
        val minutes = totalMinutes % 60
        return when {
            hours > 0 -> if (minutes > 0) "${hours}h ${minutes}min" else "${hours}h"
            totalMinutes > 0 -> "$totalMinutes min"
            else -> "${totalSeconds}s"
        }
    }

    /** Cronômetro: "07:42" ou "1:07:42". */
    fun clock(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.ROOT, "%02d:%02d", m, sec)
    }

    fun shortDay(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Seg"
        DayOfWeek.TUESDAY -> "Ter"
        DayOfWeek.WEDNESDAY -> "Qua"
        DayOfWeek.THURSDAY -> "Qui"
        DayOfWeek.FRIDAY -> "Sex"
        DayOfWeek.SATURDAY -> "Sáb"
        DayOfWeek.SUNDAY -> "Dom"
    }
}

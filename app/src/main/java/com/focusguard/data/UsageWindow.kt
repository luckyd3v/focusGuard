package com.focusguard.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.focusguard.util.TimeFormat
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Janela de utilização, ex.: "Expediente", 09:00–19:00, 10 min por desbloqueio.
 *
 * Os horários são guardados em minutos desde a meia-noite. Se o fim for menor ou igual
 * ao início, a janela atravessa a meia-noite (ex.: 22:00–06:00); início == fim = 24 h.
 * [daysMask] usa um bit por dia: bit 0 = segunda ... bit 6 = domingo. Para janelas que
 * atravessam a meia-noite, o dia considerado é o dia em que a janela começa.
 *
 * Janelas [onDemand] ignoram horário e dias: valem enquanto o usuário as mantém ligadas
 * ([activatedAt] != null) e, nesse período, sobrepõem qualquer janela por horário.
 */
@Entity(tableName = "usage_windows")
data class UsageWindow(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val startMinuteOfDay: Int,
    val endMinuteOfDay: Int,
    val limitMinutes: Int,
    val daysMask: Int = WEEKDAYS,
    val enabled: Boolean = true,
    @ColumnInfo(defaultValue = "0") val onDemand: Boolean = false,
    /** Instante em que a janela sob demanda foi ligada (null = desligada). */
    val activatedAt: Long? = null,
    /** Duração estimada da ativação atual; fica salva como sugestão para a próxima vez. */
    val estimateMinutes: Int? = null,
    /** Estimativa constante configurada na janela (null = perguntar sempre ao ligar). */
    val fixedEstimateMinutes: Int? = null,
) {
    val isOnDemandActive: Boolean get() = onDemand && enabled && activatedAt != null

    /** Quando a estimativa da janela sob demanda ligada se esgota (null = desligada ou sem estimativa). */
    val estimatedEndAt: Long?
        get() {
            val start = activatedAt ?: return null
            val minutes = estimateMinutes ?: return null
            return if (isOnDemandActive) start + minutes * 60_000L else null
        }

    fun includesDay(day: DayOfWeek): Boolean = (daysMask and dayBit(day)) != 0

    fun isActiveAt(time: LocalDateTime): Boolean {
        if (!enabled || onDemand) return false
        val minute = time.hour * 60 + time.minute
        val today = time.dayOfWeek
        return if (endMinuteOfDay > startMinuteOfDay) {
            includesDay(today) && minute >= startMinuteOfDay && minute < endMinuteOfDay
        } else {
            (minute >= startMinuteOfDay && includesDay(today)) ||
                (minute < endMinuteOfDay && includesDay(today.minus(1)))
        }
    }

    fun isActiveAt(epochMillis: Long): Boolean =
        isActiveAt(LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMillis), ZoneId.systemDefault()))

    companion object {
        const val ALL_DAYS = 0b1111111
        const val WEEKDAYS = 0b0011111
        const val WEEKEND = 0b1100000

        fun dayBit(day: DayOfWeek): Int = 1 shl (day.value - 1)
    }
}

val UsageWindow.limitMs: Long
    get() = limitMinutes * 60_000L

fun UsageWindow.rangeLabel(): String =
    "${TimeFormat.minuteOfDay(startMinuteOfDay)} às ${TimeFormat.minuteOfDay(endMinuteOfDay)}"

/** Ex.: "09:00 às 19:00 · Segunda a sexta" ou "Sob demanda". */
fun UsageWindow.scheduleLabel(): String =
    if (onDemand) "Sob demanda" else "${rangeLabel()} · ${daysLabel()}"

fun UsageWindow.daysLabel(): String = when (daysMask) {
    UsageWindow.ALL_DAYS -> "Todos os dias"
    UsageWindow.WEEKDAYS -> "Segunda a sexta"
    UsageWindow.WEEKEND -> "Fim de semana"
    else -> DayOfWeek.values().filter { includesDay(it) }.joinToString(", ") { TimeFormat.shortDay(it) }
}

package com.focusguard

import com.focusguard.data.UsageSession
import com.focusguard.data.UsageWindow
import com.focusguard.data.WindowMatcher
import com.focusguard.ui.buildDayStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class UsageWindowTest {

    private val expediente = UsageWindow(
        id = 1, name = "Expediente",
        startMinuteOfDay = 9 * 60, endMinuteOfDay = 19 * 60,
        limitMinutes = 10, daysMask = UsageWindow.WEEKDAYS,
    )

    // 22/09/2026 é uma terça-feira
    private fun at(day: Int, hour: Int, minute: Int = 0) = LocalDateTime.of(2026, 9, day, hour, minute)
    private fun millis(t: LocalDateTime) = t.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `janela comum respeita horario e dias`() {
        assertTrue(expediente.isActiveAt(at(22, 9, 0)))
        assertTrue(expediente.isActiveAt(at(22, 18, 59)))
        assertFalse(expediente.isActiveAt(at(22, 19, 0)))
        assertFalse(expediente.isActiveAt(at(22, 8, 59)))
        assertFalse(expediente.isActiveAt(at(26, 10, 0))) // sábado
    }

    @Test
    fun `janela que atravessa a meia-noite usa o dia de inicio`() {
        // Noite: 22h–06h, apenas sexta-feira
        val noite = UsageWindow(
            name = "Noite", startMinuteOfDay = 22 * 60, endMinuteOfDay = 6 * 60,
            limitMinutes = 5, daysMask = UsageWindow.dayBit(java.time.DayOfWeek.FRIDAY),
        )
        assertTrue(noite.isActiveAt(at(25, 23)))   // sexta 23h
        assertTrue(noite.isActiveAt(at(26, 5)))    // sábado 5h (começou na sexta)
        assertFalse(noite.isActiveAt(at(26, 23)))  // sábado 23h
        assertFalse(noite.isActiveAt(at(25, 5)))   // sexta 5h (começaria na quinta)
    }

    @Test
    fun `janela desativada nunca fica ativa`() {
        assertFalse(expediente.copy(enabled = false).isActiveAt(at(22, 10)))
    }

    @Test
    fun `sobreposicao escolhe o menor limite`() {
        val foco = UsageWindow(id = 2, name = "Foco", startMinuteOfDay = 14 * 60, endMinuteOfDay = 16 * 60, limitMinutes = 3)
        assertEquals("Foco", WindowMatcher.match(listOf(expediente, foco), millis(at(22, 15)))?.name)
        assertEquals("Expediente", WindowMatcher.match(listOf(expediente, foco), millis(at(22, 10)))?.name)
        assertNull(WindowMatcher.match(listOf(expediente, foco), millis(at(22, 20))))
    }

    @Test
    fun `estatisticas somam tempo e desbloqueios por janela`() {
        fun session(startH: Int, minutes: Int, windowId: Long?) = UsageSession(
            windowId = windowId, windowName = if (windowId == null) null else "Expediente",
            limitMinutes = 10, startTime = millis(at(22, startH)),
            endTime = millis(at(22, startH)) + minutes * 60_000L, exceeded = minutes > 10,
        )
        val sessions = listOf(session(9, 60, 1), session(12, 50, 1), session(16, 40, 1), session(21, 5, null))
        val stats = buildDayStats(LocalDate.of(2026, 9, 22), sessions, listOf(expediente))

        val exp = stats.perWindow.first { it.name == "Expediente" }
        assertEquals(150 * 60_000L, exp.totalMs)
        assertEquals(3, exp.unlocks)
        assertEquals(3, exp.exceeded)
        assertEquals(4, stats.unlocks)
        assertEquals("Fora das janelas", stats.perWindow.last().name)
    }
}

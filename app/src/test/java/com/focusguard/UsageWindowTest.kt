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
    fun `janela sob demanda ligada sobrepoe as janelas por horario`() {
        val reuniao = UsageWindow(
            id = 3, name = "Reunião", startMinuteOfDay = 0, endMinuteOfDay = 0,
            limitMinutes = 30, daysMask = UsageWindow.ALL_DAYS, onDemand = true,
        )
        val dez = millis(at(22, 10))
        val noite = millis(at(22, 22))

        // Desligada: vale a janela por horário e nunca "casa" pelo horário.
        assertFalse(reuniao.isActiveAt(at(22, 10)))
        assertEquals("Expediente", WindowMatcher.match(listOf(expediente, reuniao), dez)?.name)

        // Ligada: sobrepõe mesmo com limite maior, e vale também fora das janelas por horário.
        val ligada = reuniao.copy(activatedAt = dez)
        assertEquals("Reunião", WindowMatcher.match(listOf(expediente, ligada), dez)?.name)
        assertEquals("Reunião", WindowMatcher.match(listOf(expediente, ligada), noite)?.name)
        assertNull(WindowMatcher.scheduled(listOf(ligada), dez))
    }

    @Test
    fun `estimativa so vale para janela sob demanda ligada`() {
        val base = UsageWindow(
            id = 4, name = "Leitura", startMinuteOfDay = 0, endMinuteOfDay = 0,
            limitMinutes = 30, onDemand = true, estimateMinutes = 45,
        )
        val dez = millis(at(22, 10))
        assertNull(base.estimatedEndAt) // desligada: a estimativa fica só como sugestão
        assertEquals(dez + 45 * 60_000L, base.copy(activatedAt = dez).estimatedEndAt)
        assertNull(base.copy(activatedAt = dez, estimateMinutes = null).estimatedEndAt)
    }

    @Test
    fun `desbloqueio dividido por troca de janela conta uma vez no total`() {
        val leitura = UsageWindow(
            id = 5, name = "Leitura", startMinuteOfDay = 0, endMinuteOfDay = 0,
            limitMinutes = 30, onDemand = true,
        )
        val inicio = millis(at(22, 10))
        val troca = inicio + 4 * 60_000L
        val fim = troca + 20 * 60_000L
        // 4 min no Expediente, janela sob demanda ligada, mais 20 min na Leitura: um só desbloqueio.
        val sessions = listOf(
            UsageSession(windowId = 1, windowName = "Expediente", limitMinutes = 10,
                startTime = inicio, endTime = troca, exceeded = false),
            UsageSession(windowId = 5, windowName = "Leitura", limitMinutes = 30,
                startTime = troca, endTime = fim, exceeded = false, continuation = true),
        )
        val stats = buildDayStats(LocalDate.of(2026, 9, 22), sessions, listOf(expediente, leitura))

        assertEquals(1, stats.unlocks)
        assertEquals(24 * 60_000L, stats.totalMs)
        assertEquals(4 * 60_000L, stats.perWindow.first { it.name == "Expediente" }.totalMs)
        assertEquals(20 * 60_000L, stats.perWindow.first { it.name == "Leitura" }.totalMs)
        assertEquals(1, stats.perWindow.first { it.name == "Leitura" }.unlocks)
    }

    @Test
    fun `ligar sob demanda so exige codigo quando afrouxa o limite em vigor`() {
        val dez = millis(at(22, 10)) // Expediente (10 min) em vigor
        val noite = millis(at(22, 22)) // nenhuma janela em vigor
        val folga = UsageWindow(id = 7, name = "Folga", startMinuteOfDay = 0, endMinuteOfDay = 0, limitMinutes = 30, onDemand = true)
        val foco = folga.copy(id = 8, name = "Foco", limitMinutes = 5)

        assertTrue(WindowMatcher.activationLoosens(folga, listOf(expediente, folga), dez))
        assertFalse(WindowMatcher.activationLoosens(foco, listOf(expediente, foco), dez))
        assertFalse(WindowMatcher.activationLoosens(folga, listOf(expediente, folga), noite))
        // Com "Foco" (5 min) ligada, trocar para "Folga" (30 min) também afrouxa.
        val focoLigada = foco.copy(activatedAt = dez)
        assertTrue(WindowMatcher.activationLoosens(folga, listOf(expediente, focoLigada, folga), dez))
    }

    @Test
    fun `editar janela por horario ativa so exige codigo quando afrouxa`() {
        assertFalse(WindowMatcher.editLoosens(expediente, expediente.copy(name = "Trabalho")))
        assertFalse(WindowMatcher.editLoosens(expediente, expediente.copy(limitMinutes = 5)))
        assertTrue(WindowMatcher.editLoosens(expediente, expediente.copy(limitMinutes = 15)))
        assertTrue(WindowMatcher.editLoosens(expediente, expediente.copy(endMinuteOfDay = 18 * 60)))
        assertTrue(WindowMatcher.editLoosens(expediente, expediente.copy(daysMask = UsageWindow.WEEKEND)))
        assertTrue(WindowMatcher.editLoosens(expediente, expediente.copy(onDemand = true)))
        // Janela nova ou já desativada: nada a proteger.
        assertFalse(WindowMatcher.editLoosens(expediente.copy(id = 0), expediente.copy(id = 0, limitMinutes = 60)))
        assertFalse(WindowMatcher.editLoosens(expediente.copy(enabled = false), expediente.copy(enabled = false, limitMinutes = 60)))
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

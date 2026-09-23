package com.focusguard.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Uma sessão = intervalo entre um desbloqueio e o bloqueio seguinte.
 * Guarda uma cópia do nome e do limite da janela para que o histórico
 * continue legível mesmo se a janela for editada ou excluída.
 */
@Entity(
    tableName = "usage_sessions",
    indices = [Index("startTime"), Index("windowId")],
)
data class UsageSession(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val windowId: Long?,
    val windowName: String?,
    val limitMinutes: Int?,
    val startTime: Long,
    val endTime: Long,
    val exceeded: Boolean,
)

val UsageSession.durationMs: Long
    get() = (endTime - startTime).coerceAtLeast(0)

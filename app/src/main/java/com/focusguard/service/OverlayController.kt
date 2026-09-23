package com.focusguard.service

import android.content.Context
import android.graphics.PixelFormat
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.TextView
import com.focusguard.R
import com.focusguard.util.TimeFormat

/**
 * Desenha uma janela TYPE_APPLICATION_OVERLAY em tela cheia, por cima de qualquer app
 * que esteja em primeiro plano. Requer a permissão "Sobrepor a outros apps".
 */
class OverlayController(context: Context) {

    private val themedContext = ContextThemeWrapper(context, R.style.Theme_FocusGuard_Overlay)
    private val windowManager = context.getSystemService(WindowManager::class.java)

    private var root: View? = null
    private var elapsedView: TextView? = null

    val isShowing: Boolean get() = root != null

    /** @return false se o overlay não pôde ser exibido (ex.: sem permissão). */
    fun show(
        windowName: String,
        limitMinutes: Int,
        elapsedMs: Long,
        snoozeMinutes: Int,
        onStop: () -> Unit,
        onSnooze: () -> Unit,
    ): Boolean {
        if (!Settings.canDrawOverlays(themedContext)) return false
        if (root != null) {
            update(elapsedMs)
            return true
        }

        val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_alert, null)
        view.findViewById<TextView>(R.id.overlay_message).text =
            themedContext.getString(R.string.overlay_message, limitMinutes, windowName)
        elapsedView = view.findViewById(R.id.overlay_elapsed)
        view.findViewById<Button>(R.id.overlay_stop).setOnClickListener { onStop() }
        view.findViewById<Button>(R.id.overlay_snooze).apply {
            text = themedContext.getString(R.string.overlay_snooze, snoozeMinutes)
            setOnClickListener { onSnooze() }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        )

        return try {
            windowManager.addView(view, params)
            root = view
            update(elapsedMs)
            true
        } catch (e: Exception) {
            elapsedView = null
            false
        }
    }

    fun update(elapsedMs: Long) {
        elapsedView?.text = TimeFormat.clock(elapsedMs)
    }

    fun hide() {
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = null
        elapsedView = null
    }
}

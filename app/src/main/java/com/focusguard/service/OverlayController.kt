package com.focusguard.service

import android.content.Context
import android.graphics.PixelFormat
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
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
 *
 * Enquanto visível, segura o foco de áudio de forma transitória (como uma ligação): players que
 * seguem as regras do Android pausam e retomam sozinhos quando o overlay some.
 */
class OverlayController(context: Context) {

    private class Content(
        val windowName: String,
        val limitMinutes: Int,
        val snoozeMinutes: Int,
        val onIgnore: () -> Unit,
        val onSnooze: () -> Unit,
    )

    private val themedContext = ContextThemeWrapper(context, R.style.Theme_FocusGuard_Overlay)
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .setOnAudioFocusChangeListener { }
        .build()

    private var root: View? = null
    private var elapsedView: TextView? = null
    private var content: Content? = null
    private var lastElapsedMs = 0L

    val isShowing: Boolean get() = root != null

    /** @return false se o overlay não pôde ser exibido (ex.: sem permissão). */
    fun show(
        windowName: String,
        limitMinutes: Int,
        elapsedMs: Long,
        snoozeMinutes: Int,
        onIgnore: () -> Unit,
        onSnooze: () -> Unit,
    ): Boolean {
        if (!Settings.canDrawOverlays(themedContext)) return false
        if (root != null) {
            update(elapsedMs)
            return true
        }
        val c = Content(windowName, limitMinutes, snoozeMinutes, onIgnore, onSnooze)
        if (!attach(c, elapsedMs)) return false
        content = c
        // Pausa mídias em reprodução (vídeo, música, podcast) enquanto o alerta está na tela.
        runCatching { audioManager.requestAudioFocus(focusRequest) }
        return true
    }

    fun update(elapsedMs: Long) {
        lastElapsedMs = elapsedMs
        elapsedView?.text = TimeFormat.clock(elapsedMs)
    }

    fun hide() {
        detach()
        if (content != null) runCatching { audioManager.abandonAudioFocusRequest(focusRequest) }
        content = null
    }

    /** Tela girou: recria a view para usar o layout de retrato ou de paisagem. */
    fun onConfigurationChanged() {
        val c = content ?: return
        detach()
        if (!attach(c, lastElapsedMs)) hide()
    }

    private fun attach(c: Content, elapsedMs: Long): Boolean {
        val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_alert, null)
        view.findViewById<TextView>(R.id.overlay_message).text =
            themedContext.getString(R.string.overlay_message, c.limitMinutes, c.windowName)
        view.findViewById<Button>(R.id.overlay_ignore).setOnClickListener { c.onIgnore() }
        view.findViewById<Button>(R.id.overlay_snooze).apply {
            text = themedContext.getString(R.string.overlay_snooze, c.snoozeMinutes)
            setOnClickListener { c.onSnooze() }
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            // O fundo escuro cobre a tela toda, inclusive barras do sistema e recorte da câmera
            // (em paisagem o recorte fica na lateral); o card se afasta deles via fitsSystemWindows.
            layoutInDisplayCutoutMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            } else {
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) setFitInsetsTypes(0)
        }

        return try {
            windowManager.addView(view, params)
            root = view
            elapsedView = view.findViewById(R.id.overlay_elapsed)
            update(elapsedMs)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun detach() {
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = null
        elapsedView = null
    }
}

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
import android.graphics.Color
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import com.focusguard.R
import com.focusguard.data.FreeTimeSuggestion
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
        /** Usuário informou quanto tempo livre tem; quem chamou responde com [showSuggestions]. */
        val onFreeTime: (minutes: Int) -> Unit,
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
        onFreeTime: (minutes: Int) -> Unit,
    ): Boolean {
        if (!Settings.canDrawOverlays(themedContext)) return false
        if (root != null) {
            update(elapsedMs)
            return true
        }
        val c = Content(windowName, limitMinutes, snoozeMinutes, onIgnore, onSnooze, onFreeTime)
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

    /** Tela girou: recria a view para usar o layout de retrato ou de paisagem (volta ao alerta). */
    fun onConfigurationChanged() {
        val c = content ?: return
        detach()
        if (!attach(c, lastElapsedMs)) hide()
    }

    // ------------------------------------------------------------ tempo livre

    /** Troca o alerta pela pergunta "quanto tempo livre você tem?". */
    private fun showFreeTimePicker(c: Content) {
        val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_free_time, null)
        val pick = { minutes: Int -> c.onFreeTime(minutes) }
        mapOf(R.id.free_5 to 5, R.id.free_10 to 10, R.id.free_15 to 15, R.id.free_30 to 30, R.id.free_45 to 45, R.id.free_60 to 60)
            .forEach { (id, minutes) -> view.findViewById<Button>(id).setOnClickListener { pick(minutes) } }
        val custom = view.findViewById<EditText>(R.id.free_custom)
        view.findViewById<Button>(R.id.free_go).setOnClickListener {
            val minutes = custom.text.toString().toIntOrNull()
            if (minutes != null && minutes in 1..600) pick(minutes) else custom.error = "De 1 a 600 minutos"
        }
        view.findViewById<Button>(R.id.free_back).setOnClickListener { backToAlert(c) }
        swapTo(view)
    }

    /**
     * Mostra as sugestões para [minutes] de tempo livre. [onStart] fecha o overlay concedendo o
     * tempo; tocar num link chama [onOpenLink].
     */
    fun showSuggestions(
        minutes: Int,
        suggestions: List<FreeTimeSuggestion>,
        onStart: () -> Unit,
        onOpenLink: (String) -> Unit,
    ) {
        val c = content ?: return
        val view = LayoutInflater.from(themedContext).inflate(R.layout.overlay_free_time, null)
        view.findViewById<View>(R.id.free_picker).visibility = View.GONE
        view.findViewById<View>(R.id.free_results).visibility = View.VISIBLE
        val duration = TimeFormat.duration(minutes * 60_000L)
        val used = suggestions.sumOf { it.task.estimateMinutes ?: 0 }
        view.findViewById<TextView>(R.id.free_summary).text = if (suggestions.isEmpty()) {
            "Nenhuma tarefa com tempo estimado cabe em $duration. Adicione estimativas aos seus afazeres para receber sugestões."
        } else {
            "Para os seus $duration livres (${TimeFormat.duration(used * 60_000L)} planejados):"
        }
        val list = view.findViewById<LinearLayout>(R.id.free_list)
        suggestions.forEach { list.addView(suggestionView(it, onOpenLink)) }
        view.findViewById<Button>(R.id.free_start).apply {
            text = themedContext.getString(R.string.free_start, duration)
            setOnClickListener { onStart() }
        }
        view.findViewById<Button>(R.id.free_back).setOnClickListener { showFreeTimePicker(c) }
        swapTo(view)
    }

    private fun suggestionView(s: FreeTimeSuggestion, onOpenLink: (String) -> Unit): View {
        val density = themedContext.resources.displayMetrics.density
        val item = LinearLayout(themedContext).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (10 * density).toInt()
            setPadding(0, pad, 0, pad)
        }
        val url = s.task.url
        item.addView(TextView(themedContext).apply {
            text = if (url != null) "${s.task.title}  ↗" else s.task.title
            setTextColor(Color.WHITE)
            textSize = 16f
        })
        item.addView(TextView(themedContext).apply {
            text = "${s.category.label} · ${TimeFormat.duration((s.task.estimateMinutes ?: 0) * 60_000L)}"
            setTextColor(themedContext.getColor(R.color.overlay_text_secondary))
            textSize = 13f
        })
        if (url != null) {
            item.isClickable = true
            item.setOnClickListener { onOpenLink(url) }
        }
        return item
    }

    private fun backToAlert(c: Content) {
        detach()
        if (!attach(c, lastElapsedMs)) hide()
    }

    /** Coloca [view] na janela do overlay no lugar da atual (adiciona antes de remover, sem piscar). */
    private fun swapTo(view: View) {
        val old = root
        try {
            windowManager.addView(view, newParams())
        } catch (e: Exception) {
            return
        }
        old?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = view
        elapsedView = null
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
        view.findViewById<Button>(R.id.overlay_free).setOnClickListener { showFreeTimePicker(c) }

        return try {
            windowManager.addView(view, newParams())
            root = view
            elapsedView = view.findViewById(R.id.overlay_elapsed)
            update(elapsedMs)
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun newParams(): WindowManager.LayoutParams {
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
            // Deixa o teclado empurrar o conteúdo ao digitar o tempo livre.
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
        }
        return params
    }

    private fun detach() {
        root?.let { runCatching { windowManager.removeViewImmediate(it) } }
        root = null
        elapsedView = null
    }
}

package com.focusguard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.ClipboardManager
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalTextToolbar
import androidx.compose.ui.platform.TextToolbar
import androidx.compose.ui.platform.TextToolbarStatus
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import java.security.SecureRandom

private const val CODE_LENGTH = 36

/** Sem caracteres que se confundem ao ler (0/o, 1/l/i). */
private const val CODE_ALPHABET = "abcdefghjkmnpqrstuvwxyz23456789"

private val random = SecureRandom()

private fun newCode(): String = buildString { repeat(CODE_LENGTH) { append(CODE_ALPHABET[random.nextInt(CODE_ALPHABET.length)]) } }

/**
 * Pede que o usuário digite um código aleatório de 36 caracteres antes de uma ação que afrouxa
 * os limites (desativar ou excluir uma janela por horário). O código não pode ser copiado nem
 * colado:
 *  - é desenhado como imagem (não é texto selecionável nem lido por serviços de acessibilidade);
 *  - o diálogo usa FLAG_SECURE, bloqueando captura de tela, gravação e OCR (ex.: Google Lens);
 *  - o campo não tem menu de copiar/colar, ignora a área de transferência, usa teclado de senha
 *    (sem sugestões nem atalho de clipboard) e rejeita qualquer entrada que acrescente mais de um
 *    caractere de uma vez — o que barra colagem pelo teclado, preenchimento automático e arrastar.
 */
@Composable
fun ConfirmCodeDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val code = remember { newCode() }
    var typed by remember { mutableStateOf("") }
    val matches = typed == code
    val prefixOk = code.startsWith(typed)

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(securePolicy = SecureFlagPolicy.SecureOn),
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(message, style = MaterialTheme.typography.bodyMedium)
                CodeImage(code)
                NoClipboard {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { new ->
                            // Digitação é um caractere por vez; mais que isso é colagem ou autofill.
                            if (new.length <= typed.length + 1) {
                                typed = new.lowercase().filter { it in CODE_ALPHABET }.take(CODE_LENGTH)
                            }
                        },
                        label = { Text("Digite o código") },
                        singleLine = true,
                        isError = !prefixOk,
                        supportingText = {
                            Text(
                                when {
                                    matches -> "Código correto"
                                    !prefixOk -> "Há um caractere errado"
                                    else -> "${typed.length}/$CODE_LENGTH"
                                }
                            )
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, autoCorrectEnabled = false),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(enabled = matches, onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/** Código em blocos de 4, desenhado no Canvas: não é texto, então não há o que selecionar. */
@Composable
private fun CodeImage(code: String) {
    val measurer = rememberTextMeasurer()
    val color = MaterialTheme.colorScheme.onSurface
    val style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = color)
    val lines = code.chunked(12).map { it.chunked(4).joinToString("  ") }
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(96.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clearAndSetSemantics { },
    ) {
        val lineHeight = size.height / lines.size
        lines.forEachIndexed { i, line ->
            val layout = measurer.measure(AnnotatedString(line), style)
            drawText(
                layout,
                topLeft = Offset((size.width - layout.size.width) / 2f, i * lineHeight + (lineHeight - layout.size.height) / 2f),
            )
        }
    }
}

/** Remove o menu de copiar/colar e desliga a área de transferência para o conteúdo. */
@Composable
private fun NoClipboard(content: @Composable () -> Unit) {
    val toolbar = remember {
        object : TextToolbar {
            override val status: TextToolbarStatus = TextToolbarStatus.Hidden
            override fun hide() {}
            override fun showMenu(
                rect: Rect,
                onCopyRequested: (() -> Unit)?,
                onPasteRequested: (() -> Unit)?,
                onCutRequested: (() -> Unit)?,
                onSelectAllRequested: (() -> Unit)?,
            ) {}
        }
    }
    val clipboard = remember {
        object : ClipboardManager {
            override fun setText(annotatedString: AnnotatedString) {}
            override fun getText(): AnnotatedString? = null
            override fun hasText(): Boolean = false
        }
    }
    CompositionLocalProvider(LocalTextToolbar provides toolbar, LocalClipboardManager provides clipboard, content = content)
}

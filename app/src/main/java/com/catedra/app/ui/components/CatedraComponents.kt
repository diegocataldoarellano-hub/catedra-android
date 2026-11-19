package com.catedra.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catedra.app.data.model.DocType
import com.catedra.app.media.TtsUiState
import com.catedra.app.ui.theme.LocalCatedraColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun DocAvatar(
    docType: DocType,
    size: Dp = 46.dp
) {
    val bgColor = when (docType) {
        DocType.PDF -> Color(0xFFE05A5A)
        DocType.DOCX, DocType.DOC -> Color(0xFF3A82D6)
        DocType.XLSX, DocType.XLS, DocType.CSV -> Color(0xFF2D9C63)
        DocType.PPTX -> Color(0xFFD97C32)
        DocType.EPUB -> Color(0xFF7B61D9)
        DocType.IMAGE -> Color(0xFF9C54B8)
        DocType.PAPER -> Color(0xFF2AABEE)
        DocType.TEXT, DocType.UNKNOWN -> Color(0xFF607D8B)
    }

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bgColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = docType.shortBadge,
            color = Color.White,
            fontSize = if (size >= 44.dp) 11.5.sp else 9.5.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.3.sp,
            maxLines = 1
        )
    }
}

@Composable
fun TtsMiniPlayerBar(
    ttsState: TtsUiState,
    onTogglePlayPause: () -> Unit,
    onPrevSection: () -> Unit,
    onNextSection: () -> Unit,
    onCycleSpeed: () -> Unit,
    onStop: () -> Unit
) {
    if (!ttsState.isPlaying && ttsState.currentTitle.isBlank()) return
    val colors = LocalCatedraColors.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onTogglePlayPause, modifier = Modifier.size(34.dp)) {
                Icon(
                    imageVector = if (ttsState.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    contentDescription = "Reproducir o pausar",
                    tint = colors.accent,
                    modifier = Modifier.size(22.dp)
                )
            }
            if (ttsState.isDocumentReading) {
                IconButton(onClick = onPrevSection, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Anterior", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
                IconButton(onClick = onNextSection, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Siguiente", tint = colors.textSecondary, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = ttsState.currentTitle.ifBlank { "Lectura en voz alta" },
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (ttsState.isDocumentReading && ttsState.totalSections > 0) {
                    Text(
                        text = "Sección ${ttsState.currentSectionIndex + 1} de ${ttsState.totalSections}",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.accentSoft)
                    .clickable(onClick = onCycleSpeed)
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = String.format(Locale.US, "%.2gx", ttsState.speechRate),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
            IconButton(onClick = onStop, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Rounded.Close, contentDescription = "Detener", tint = colors.textMuted, modifier = Modifier.size(18.dp))
            }
        }
        if (ttsState.totalChunksInSection > 0) {
            val progress = ((ttsState.currentChunkIndex + 1).toFloat() / ttsState.totalChunksInSection).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = colors.accent,
                trackColor = colors.divider
            )
        } else {
            HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
        }
    }
}

/**
 * Renderiza texto de estudio con soporte limpio de Markdown ligero:
 * **negrita**, *cursiva*, `código`, encabezados, citas y viñetas.
 */
@Composable
fun FormattedStudyText(
    text: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = LocalCatedraColors.current
    val annotated = buildMarkdownAnnotatedString(text, colors.citationText)
    Text(
        text = annotated,
        style = MaterialTheme.typography.bodyLarge,
        color = color,
        modifier = modifier
    )
}

private fun buildMarkdownAnnotatedString(raw: String, highlightColor: Color): AnnotatedString {
    return buildAnnotatedString {
        val lines = raw.lines()
        lines.forEachIndexed { lineIdx, origLine ->
            val trimmed = origLine.trimStart()
            when {
                trimmed.startsWith("### ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp)) {
                        appendInlineFormatted(trimmed.removePrefix("### "), highlightColor)
                    }
                }
                trimmed.startsWith("## ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp)) {
                        appendInlineFormatted(trimmed.removePrefix("## "), highlightColor)
                    }
                }
                trimmed.startsWith("# ") -> {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontSize = 16.5.sp)) {
                        appendInlineFormatted(trimmed.removePrefix("# "), highlightColor)
                    }
                }
                trimmed.startsWith("> ") -> {
                    withStyle(SpanStyle(fontStyle = FontStyle.Italic, color = highlightColor)) {
                        append("❝ ")
                        appendInlineFormatted(trimmed.removePrefix("> "), highlightColor)
                    }
                }
                trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                    append("• ")
                    appendInlineFormatted(trimmed.substring(2), highlightColor)
                }
                else -> appendInlineFormatted(origLine, highlightColor)
            }
            if (lineIdx < lines.lastIndex) append("\n")
        }
    }
}

private fun AnnotatedString.Builder.appendInlineFormatted(line: String, citationColor: Color) {
    // Tokeniza **bold**, *italic*, `code`, y [Pág. X]
    val regex = Regex("""(\*\*[^*]+\*\*|\*[^*]+\*|`[^`]+`|\[(?:Pág\.|Sección|Hoja:|Diap\.|Cap\.|Tabla)[^\]]*\])""")
    var cursor = 0
    for (m in regex.findAll(line)) {
        if (m.range.first > cursor) {
            append(line.substring(cursor, m.range.first))
        }
        val token = m.value
        when {
            token.startsWith("**") && token.endsWith("**") -> {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) {
                    append(token.substring(2, token.length - 2))
                }
            }
            token.startsWith("*") && token.endsWith("*") -> {
                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                    append(token.substring(1, token.length - 1))
                }
            }
            token.startsWith("`") && token.endsWith("`") -> {
                withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.5.sp)) {
                    append(token.substring(1, token.length - 1))
                }
            }
            token.startsWith("[") && token.endsWith("]") -> {
                withStyle(SpanStyle(color = citationColor, fontWeight = FontWeight.SemiBold)) {
                    append(token)
                }
            }
            else -> append(token)
        }
        cursor = m.range.last + 1
    }
    if (cursor < line.length) {
        append(line.substring(cursor))
    }
}

fun formatRelativeTime(epochMs: Long): String {
    val now = Calendar.getInstance()
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val sameDay = now.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    if (sameDay) {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epochMs))
    }
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    val isYesterday = yesterday.get(Calendar.YEAR) == cal.get(Calendar.YEAR) &&
        yesterday.get(Calendar.DAY_OF_YEAR) == cal.get(Calendar.DAY_OF_YEAR)
    if (isYesterday) return "Ayer"
    return SimpleDateFormat("d MMM", Locale("es", "ES")).format(Date(epochMs))
}

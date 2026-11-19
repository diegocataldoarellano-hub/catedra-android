package com.catedra.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catedra.app.data.model.QuizQuestion
import com.catedra.app.data.model.QuizSessionState
import com.catedra.app.ui.theme.LocalCatedraColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    quizState: QuizSessionState,
    onBack: () -> Unit,
    onRegenerateQuiz: () -> Unit,
    onSelectOption: (questionId: Int, optionIndex: Int) -> Unit,
    onCitationClick: (String) -> Unit,
    onSaveMistakesToNotes: () -> Unit,
    onBuildFormsAppsScript: () -> String,
    onOpenUrlInBrowser: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val colors = LocalCatedraColors.current
    val context = LocalContext.current
    var showFormsSheet by remember { mutableStateOf(false) }

    val answeredCount = quizState.selectedAnswers.size
    val correctCount = quizState.questions.count { q ->
        quizState.selectedAnswers[q.id] == q.correctIndex
    }
    val mistakesCount = quizState.questions.count { q ->
        val sel = quizState.selectedAnswers[q.id]
        sel != null && sel != q.correctIndex
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.topBar)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = colors.textPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Quiz Diagnóstico y Formularios",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary
                )
                Text(
                    text = "${quizState.documentTitle} · $correctCount/${quizState.questions.size} correctas",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (quizState.questions.isNotEmpty()) {
                IconButton(onClick = { showFormsSheet = true }) {
                    Icon(Icons.Rounded.Description, contentDescription = "Exportar a Google Forms", tint = colors.accent)
                }
            }
            IconButton(onClick = onRegenerateQuiz, enabled = !quizState.isLoading) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Nuevo cuestionario", tint = colors.textSecondary)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        when {
            quizState.isLoading -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = colors.accent)
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = "El Docente está diseñando preguntas de comprensión...",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            quizState.error != null -> {
                Box(modifier = Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(quizState.error, style = MaterialTheme.typography.bodyMedium, color = colors.danger)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.accent)
                                .clickable(onClick = onRegenerateQuiz)
                                .padding(horizontal = 18.dp, vertical = 10.dp)
                        ) {
                            Text("Reintentar", color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    items(quizState.questions, key = { it.id }) { q ->
                        QuizQuestionCard(
                            q = q,
                            selectedIndex = quizState.selectedAnswers[q.id],
                            onSelect = { idx -> onSelectOption(q.id, idx) },
                            onCitationClick = onCitationClick
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }

                // Barra inferior de diagnóstico y exportación a Google Forms
                HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .navigationBarsPadding()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (mistakesCount > 0) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.accentSoft)
                                .clickable(onClick = onSaveMistakesToNotes)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, tint = colors.accent, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Guardar $mistakesCount error(es) en .md",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.accent
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(16.dp))
                                .background(colors.surfaceElevated)
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Rounded.Quiz, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Respondidas: $answeredCount / ${quizState.questions.size}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colors.textSecondary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.accent)
                            .clickable { showFormsSheet = true }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Description, contentDescription = null, tint = Color.White, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Google Forms", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }
        }
    }

    if (showFormsSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFormsSheet = false },
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Exportar cuestionario a Google Forms", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    text = "Crea un Google Form autocalificable real (con respuestas correctas y retroalimentación de errores) en tu cuenta de Google en 10 segundos.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accentSoft)
                        .clickable {
                            val script = onBuildFormsAppsScript()
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Catedra Google Forms Script", script))
                            showFormsSheet = false
                            onShowMessage("Script copiado. En script.new pégalo y toca Ejecutar para crear tu Google Form.")
                            onOpenUrlInBrowser("https://script.new")
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Code, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Crear Google Form automático (Apps Script)", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        Text("Copia el generador y abre script.new para crear el formulario con puntaje y feedback.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceElevated)
                        .clickable {
                            val plain = buildString {
                                appendLine("Cuestionario Cátedra: ${quizState.documentTitle}")
                                appendLine()
                                quizState.questions.forEach { q ->
                                    appendLine("${q.id}. ${q.question} [${q.citationRef}]")
                                    q.options.forEachIndexed { idx, opt ->
                                        val mark = if (idx == q.correctIndex) " (Correcta)" else ""
                                        appendLine("   ${('A' + idx)}) $opt$mark")
                                    }
                                    appendLine("   Explicación: ${q.explanation}")
                                    appendLine()
                                }
                            }
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("Cuestionario Cátedra", plain))
                            showFormsSheet = false
                            onShowMessage("Preguntas copiadas. Abriendo Google Forms...")
                            onOpenUrlInBrowser("https://forms.new")
                        }
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, tint = colors.accent, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Copiar preguntas + Abrir forms.new", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                        Text("Copia el cuestionario en texto limpio y abre un formulario nuevo de Google.", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun QuizQuestionCard(
    q: QuizQuestion,
    selectedIndex: Int?,
    onSelect: (Int) -> Unit,
    onCitationClick: (String) -> Unit
) {
    val colors = LocalCatedraColors.current
    val answered = selectedIndex != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.surface)
            .border(0.6.dp, colors.divider, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "PREGUNTA ${q.id}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.accent,
                fontWeight = FontWeight.Bold
            )
            if (q.citationRef.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(colors.citationBg)
                        .clickable { onCitationClick(q.citationRef) }
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text("↗ ${q.citationRef}", style = MaterialTheme.typography.labelSmall, color = colors.citationText, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Text(text = q.question, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary, lineHeight = 20.sp)

        q.options.forEachIndexed { idx, opt ->
            val isChosen = selectedIndex == idx
            val isCorrect = idx == q.correctIndex
            val bg = when {
                !answered -> colors.surfaceElevated
                isCorrect -> colors.success.copy(alpha = 0.16f)
                isChosen && !isCorrect -> colors.danger.copy(alpha = 0.15f)
                else -> colors.surfaceElevated.copy(alpha = 0.5f)
            }
            val border = when {
                !answered -> colors.divider
                isCorrect -> colors.success
                isChosen && !isCorrect -> colors.danger
                else -> Color.Transparent
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(bg)
                    .border(0.8.dp, border, RoundedCornerShape(10.dp))
                    .clickable(enabled = !answered) { onSelect(idx) }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${('A' + idx)}.",
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.textSecondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(22.dp)
                )
                Text(
                    text = opt,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                if (answered && isCorrect) {
                    Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(18.dp))
                } else if (answered && isChosen) {
                    Icon(Icons.Rounded.Close, contentDescription = null, tint = colors.danger, modifier = Modifier.size(18.dp))
                }
            }
        }

        if (answered) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.surfaceElevated)
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Sustento en el texto:",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.success,
                    fontWeight = FontWeight.Bold
                )
                Text(q.explanation, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                if (q.commonMisconception.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "Error de comprensión frecuente:",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.warning,
                        fontWeight = FontWeight.Bold
                    )
                    Text(q.commonMisconception, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
        }
    }
}

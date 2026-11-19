package com.catedra.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BookmarkAdd
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Style
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import com.catedra.app.data.model.FlashcardItem
import com.catedra.app.data.model.QuizDifficulty
import com.catedra.app.data.model.QuizQuestion
import com.catedra.app.data.model.QuizSessionState
import com.catedra.app.ui.components.FormattedStudyText
import com.catedra.app.ui.theme.LocalCatedraColors

/**
 * Studio de Estudio estilo NotebookLM:
 * 1) Quiz Progresivo (Fácil -> Medio -> Difícil) con selector de cantidad (5/8/10/15),
 *    pistas pedagógicas antes de responder, diagnóstico de errores y exportación a Google Forms.
 * 2) Tarjetas (Flashcards) interactivas con giro, pista y guardado de conceptos dominados en .md.
 * 3) Guía de Estudio completa (Mapa de ideas, Glosario citado, Preguntas de desarrollo) con lectura TTS.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(
    quizState: QuizSessionState,
    onBack: () -> Unit,
    onUpdateConfigAndGenerate: (count: Int, difficulty: QuizDifficulty) -> Unit,
    onSelectOption: (questionId: Int, optionIndex: Int) -> Unit,
    onToggleHint: (questionId: Int) -> Unit,
    onAskDocenteAboutQuestion: (QuizQuestion) -> Unit,
    onCitationClick: (String) -> Unit,
    onSaveMistakesToNotes: () -> Unit,
    onGenerateFlashcards: () -> Unit,
    onSaveFlashcardMastered: (FlashcardItem) -> Unit,
    onGenerateStudyGuide: () -> Unit,
    onSpeakStudyGuide: (String) -> Unit,
    onSaveStudyGuideToNotes: () -> Unit,
    onBuildFormsAppsScript: () -> String,
    onOpenUrlInBrowser: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val colors = LocalCatedraColors.current
    val context = LocalContext.current

    var activeTab by remember(quizState.documentId) { mutableStateOf("Quiz") } // "Quiz", "Tarjetas", "Guía"
    var selectedCount by remember(quizState.documentId, quizState.questionCount) { mutableIntStateOf(quizState.questionCount) }
    var selectedDifficulty by remember(quizState.documentId, quizState.difficulty) { mutableStateOf(quizState.difficulty) }
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
        // Top Bar
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
                    text = "Studio · Quiz y Guía",
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
            IconButton(
                onClick = {
                    when (activeTab) {
                        "Quiz" -> onUpdateConfigAndGenerate(selectedCount, selectedDifficulty)
                        "Tarjetas" -> onGenerateFlashcards()
                        else -> onGenerateStudyGuide()
                    }
                },
                enabled = !quizState.isLoading && !quizState.isLoadingFlashcards && !quizState.isLoadingStudyGuide
            ) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Regenerar", tint = colors.textSecondary)
            }
        }

        // Pestañas estilo NotebookLM Studio (Quiz / Tarjetas / Guía)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.topBar)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(22.dp)
        ) {
            listOf("Quiz", "Tarjetas", "Guía").forEach { tab ->
                val sel = tab == activeTab
                Column(
                    modifier = Modifier
                        .clickable {
                            activeTab = tab
                            if (tab == "Tarjetas" && quizState.flashcards.isEmpty() && !quizState.isLoadingFlashcards) {
                                onGenerateFlashcards()
                            } else if (tab == "Guía" && quizState.studyGuideMarkdown.isBlank() && !quizState.isLoadingStudyGuide) {
                                onGenerateStudyGuide()
                            }
                        }
                        .padding(top = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = tab,
                        style = MaterialTheme.typography.labelMedium,
                        fontSize = 13.5.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (sel) colors.accent else colors.textSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Box(
                        modifier = Modifier
                            .width(if (sel) 34.dp else 0.dp)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (sel) colors.accent else Color.Transparent)
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        when (activeTab) {
            "Quiz" -> {
                // Panel de configuración compacta (Cantidad 5/8/10/15 + Progresión Fácil -> Difícil)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surfaceElevated)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Preguntas:", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        listOf(5, 8, 10, 15).forEach { cnt ->
                            val sel = selectedCount == cnt
                            StudioChoiceChip("$cnt", sel) { selectedCount = cnt }
                        }
                        Spacer(Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(14.dp))
                                .background(colors.accent)
                                .clickable(enabled = !quizState.isLoading) {
                                    onUpdateConfigAndGenerate(selectedCount, selectedDifficulty)
                                }
                                .padding(horizontal = 11.dp, vertical = 5.dp)
                        ) {
                            Text("Generar", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Nivel:", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                        QuizDifficulty.entries.forEach { diff ->
                            StudioChoiceChip(diff.label, selectedDifficulty == diff) {
                                selectedDifficulty = diff
                            }
                        }
                    }
                }
                if (quizState.questions.isNotEmpty()) {
                    val progress = (answeredCount.toFloat() / quizState.questions.size).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().height(2.5.dp),
                        color = colors.accent,
                        trackColor = colors.divider
                    )
                } else {
                    HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
                }

                when {
                    quizState.isLoading -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = colors.accent)
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    text = "Creando $selectedCount preguntas (${selectedDifficulty.label}) con pistas...",
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
                                        .clickable { onUpdateConfigAndGenerate(selectedCount, selectedDifficulty) }
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
                                    hintRevealed = q.id in quizState.revealedHints,
                                    onSelect = { idx -> onSelectOption(q.id, idx) },
                                    onToggleHint = { onToggleHint(q.id) },
                                    onAskDocente = { onAskDocenteAboutQuestion(q) },
                                    onCitationClick = onCitationClick
                                )
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }

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
                                    Text("Guardar $mistakesCount error(es) en .md", style = MaterialTheme.typography.labelMedium, color = colors.accent)
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
                                    Text("Progreso: $answeredCount / ${quizState.questions.size}", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
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

            "Tarjetas" -> {
                FlashcardsStudioPane(
                    cards = quizState.flashcards,
                    isLoading = quizState.isLoadingFlashcards,
                    onGenerate = onGenerateFlashcards,
                    onCitationClick = onCitationClick,
                    onSaveMastered = onSaveFlashcardMastered
                )
            }

            else -> {
                StudyGuideStudioPane(
                    markdown = quizState.studyGuideMarkdown,
                    isLoading = quizState.isLoadingStudyGuide,
                    onGenerate = onGenerateStudyGuide,
                    onSpeak = { onSpeakStudyGuide(quizState.studyGuideMarkdown) },
                    onSaveToNotes = onSaveStudyGuideToNotes
                )
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
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Exportar cuestionario a Google Forms", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(
                    text = "Exporta las ${quizState.questions.size} preguntas (${quizState.difficulty.label}) como Google Form autocalificable con puntaje y retroalimentación.",
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
                                    appendLine("${q.id}. [${q.difficultyLabel}] ${q.question} [${q.citationRef}]")
                                    if (q.hint.isNotBlank()) appendLine("   Pista: ${q.hint}")
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
private fun StudioChoiceChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalCatedraColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(if (selected) colors.accentSoft else colors.surface)
            .border(0.6.dp, if (selected) colors.accent else colors.divider, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textSecondary,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
        )
    }
}

@Composable
private fun DifficultyBadge(level: String) {
    val colors = LocalCatedraColors.current
    val low = level.lowercase()
    val tint = when {
        low.contains("fácil") || low.contains("facil") -> colors.success
        low.contains("difícil") || low.contains("dificil") -> Color(0xFF9C54B8)
        else -> colors.warning
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = level.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun QuizQuestionCard(
    q: QuizQuestion,
    selectedIndex: Int?,
    hintRevealed: Boolean,
    onSelect: (Int) -> Unit,
    onToggleHint: () -> Unit,
    onAskDocente: () -> Unit,
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
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "PREGUNTA ${q.id}",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    fontWeight = FontWeight.Bold
                )
                DifficultyBadge(q.difficultyLabel)
            }
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

        // Botón "Ver pista" estilo NotebookLM (antes de responder)
        if (q.hint.isNotBlank()) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.warning.copy(alpha = 0.12f))
                    .clickable(onClick = onToggleHint)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = colors.warning, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (hintRevealed) "Ocultar pista" else "Ver pista (sin revelar respuesta)",
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.warning,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (hintRevealed) {
                Text(
                    text = q.hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.surfaceElevated)
                        .padding(10.dp)
                )
            }
        }

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
                Text("Sustento en el texto:", style = MaterialTheme.typography.labelSmall, color = colors.success, fontWeight = FontWeight.Bold)
                Text(q.explanation, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                if (q.commonMisconception.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text("Error de comprensión frecuente:", style = MaterialTheme.typography.labelSmall, color = colors.warning, fontWeight = FontWeight.Bold)
                    Text(q.commonMisconception, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
                Spacer(Modifier.height(4.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.accentSoft)
                        .clickable(onClick = onAskDocente)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Conversar esta duda con el Docente", style = MaterialTheme.typography.labelSmall, color = colors.accent)
                }
            }
        }
    }
}

@Composable
private fun FlashcardsStudioPane(
    cards: List<FlashcardItem>,
    isLoading: Boolean,
    onGenerate: () -> Unit,
    onCitationClick: (String) -> Unit,
    onSaveMastered: (FlashcardItem) -> Unit
) {
    val colors = LocalCatedraColors.current
    var cardIndex by remember(cards.size) { mutableIntStateOf(0) }
    var flipped by remember(cardIndex, cards.size) { mutableStateOf(false) }
    var showHint by remember(cardIndex, cards.size) { mutableStateOf(false) }

    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Generando tarjetas de estudio estilo NotebookLM...", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
        }

        cards.isEmpty() -> {
            Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.Style, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                    Text("Tarjetas de recuerdo activo (de fácil a complejo)", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.accent)
                            .clickable(onClick = onGenerate)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Generar Tarjetas", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        else -> {
            val safeIdx = cardIndex.coerceIn(0, cards.lastIndex)
            val card = cards[safeIdx]
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(18.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Tarjeta ${safeIdx + 1} de ${cards.size}", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        DifficultyBadge(card.difficultyLabel)
                    }
                    if (card.citationRef.isNotBlank()) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(colors.citationBg)
                                .clickable { onCitationClick(card.citationRef) }
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text("↗ ${card.citationRef}", style = MaterialTheme.typography.labelSmall, color = colors.citationText, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }

                // Tarjeta interactiva volteable
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(vertical = 18.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (flipped) colors.accentSoft else colors.surfaceElevated)
                        .border(1.dp, if (flipped) colors.accent else colors.divider, RoundedCornerShape(20.dp))
                        .clickable { flipped = !flipped }
                        .padding(22.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (flipped) "RESPUESTA" else "PREGUNTA / CONCEPTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = if (flipped) card.back else card.front,
                        style = MaterialTheme.typography.titleMedium.copy(lineHeight = 24.sp),
                        color = colors.textPrimary
                    )
                    if (!flipped && card.hint.isNotBlank()) {
                        Spacer(Modifier.height(16.dp))
                        if (showHint) {
                            Text(
                                text = "Pista: ${card.hint}",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.warning
                            )
                        } else {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(colors.warning.copy(alpha = 0.14f))
                                    .clickable { showHint = true }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Lightbulb, contentDescription = null, tint = colors.warning, modifier = Modifier.size(15.dp))
                                Spacer(Modifier.width(5.dp))
                                Text("Ver pista", style = MaterialTheme.typography.labelSmall, color = colors.warning, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        text = if (flipped) "Toca para volver a la pregunta" else "Toca la tarjeta para ver la respuesta",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textMuted
                    )
                }

                // Controles inferiores
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (safeIdx > 0) cardIndex = safeIdx - 1 }, enabled = safeIdx > 0) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowLeft, contentDescription = "Anterior", tint = if (safeIdx > 0) colors.textPrimary else colors.textMuted)
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.success.copy(alpha = 0.16f))
                            .clickable {
                                onSaveMastered(card)
                                if (safeIdx < cards.lastIndex) cardIndex = safeIdx + 1
                            }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = colors.success, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lo domino (guardar .md)", style = MaterialTheme.typography.labelMedium, color = colors.success)
                    }
                    IconButton(onClick = { if (safeIdx < cards.lastIndex) cardIndex = safeIdx + 1 }, enabled = safeIdx < cards.lastIndex) {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = "Siguiente", tint = if (safeIdx < cards.lastIndex) colors.textPrimary else colors.textMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun StudyGuideStudioPane(
    markdown: String,
    isLoading: Boolean,
    onGenerate: () -> Unit,
    onSpeak: () -> Unit,
    onSaveToNotes: () -> Unit
) {
    val colors = LocalCatedraColors.current
    when {
        isLoading -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = colors.accent)
                    Spacer(Modifier.height(12.dp))
                    Text("Elaborando Guía de Estudio estilo NotebookLM...", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }
        }

        markdown.isBlank() -> {
            Box(modifier = Modifier.fillMaxSize().padding(28.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
                    Text("Guía de Estudio: Mapa de ideas, Glosario citado y Preguntas de ensayo", style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.accent)
                            .clickable(onClick = onGenerate)
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Text("Generar Guía de Estudio", color = Color.White, style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        else -> {
            Column(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(18.dp)
                ) {
                    FormattedStudyText(text = markdown, color = colors.textPrimary)
                }
                HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.surface)
                        .navigationBarsPadding()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.surfaceElevated)
                            .clickable(onClick = onSpeak)
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Escuchar Guía", style = MaterialTheme.typography.labelMedium, color = colors.textPrimary)
                    }
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.accent)
                            .clickable(onClick = onSaveToNotes)
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.BookmarkAdd, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Guardar en .md", style = MaterialTheme.typography.labelMedium, color = Color.White)
                    }
                }
            }
        }
    }
}

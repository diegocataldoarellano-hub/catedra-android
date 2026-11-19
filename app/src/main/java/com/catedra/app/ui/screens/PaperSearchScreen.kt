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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.CompareArrows
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FormatQuote
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catedra.app.data.model.PaperResult
import com.catedra.app.data.remote.AcademicPaperService
import com.catedra.app.ui.CatedraUiState
import com.catedra.app.ui.theme.LocalCatedraColors

@Composable
fun PaperSearchScreen(
    uiState: CatedraUiState,
    initialQuery: String,
    contextDocId: String?,
    onBack: () -> Unit,
    onSearch: (query: String, onlyOa: Boolean, source: String, sortCited: Boolean) -> Unit,
    onOpenGoogleScholar: (String) -> Unit,
    onImportUrlOrDoi: (String) -> Unit,
    onOpenWebUrl: (String) -> Unit,
    onFormatApa: (PaperResult) -> String,
    onFormatBibTex: (PaperResult) -> String,
    onShowMessage: (String) -> Unit,
    onImportPdf: (PaperResult) -> Unit,
    onStudyAbstract: (PaperResult) -> Unit,
    onCompareInChat: (PaperResult, String) -> Unit
) {
    val colors = LocalCatedraColors.current
    val context = LocalContext.current

    var query by remember(initialQuery) { mutableStateOf(initialQuery) }
    var onlyOa by remember { mutableStateOf(false) }
    var sourceFilter by remember { mutableStateOf("ALL") }
    var sortCited by remember { mutableStateOf(false) }
    var showImportLinkDialog by remember { mutableStateOf(false) }
    var paperForCiteDialog by remember { mutableStateOf<PaperResult?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Barra de búsqueda minimalista
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.topBar)
                .statusBarsPadding()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver", tint = colors.textPrimary)
            }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Tema, autor, libro gratuito o DOI...", color = colors.textMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch(query, onlyOa, sourceFilter, sortCited) }),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = colors.textPrimary,
                    unfocusedTextColor = colors.textPrimary
                ),
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { onSearch(query, onlyOa, sourceFilter, sortCited) }) {
                Icon(Icons.Rounded.Search, contentDescription = "Buscar", tint = colors.accent)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        // Fila 1: Modos de búsqueda corroborable (Todo, Solo DOI, Libros gratis, Por Autor, arXiv)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            PaperFilterChip("Todo corroborable", sourceFilter == AcademicPaperService.MODE_ALL) {
                sourceFilter = AcademicPaperService.MODE_ALL
                if (query.isNotBlank()) onSearch(query, onlyOa, sourceFilter, sortCited)
            }
            PaperFilterChip("Solo con DOI", sourceFilter == AcademicPaperService.MODE_DOI_ONLY) {
                sourceFilter = AcademicPaperService.MODE_DOI_ONLY
                if (query.isNotBlank()) onSearch(query, onlyOa, sourceFilter, sortCited)
            }
            PaperFilterChip("Libros gratuitos", sourceFilter == AcademicPaperService.MODE_FREE_BOOKS) {
                sourceFilter = AcademicPaperService.MODE_FREE_BOOKS
                if (query.isNotBlank()) onSearch(query, onlyOa, sourceFilter, sortCited)
            }
            PaperFilterChip("Por Autor", sourceFilter == AcademicPaperService.MODE_AUTHOR) {
                sourceFilter = AcademicPaperService.MODE_AUTHOR
                if (query.isNotBlank()) onSearch(query, onlyOa, sourceFilter, sortCited)
            }
            PaperFilterChip("arXiv", sourceFilter == AcademicPaperService.MODE_ARXIV) {
                sourceFilter = AcademicPaperService.MODE_ARXIV
                if (query.isNotBlank()) onSearch(query, onlyOa, sourceFilter, sortCited)
            }
        }

        // Fila 2: Filtros de descarga, citas, Google Scholar e importación directa DOI/URL
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(start = 12.dp, end = 12.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.accent)
                    .clickable { onOpenGoogleScholar(query) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.School, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Google Scholar", style = MaterialTheme.typography.labelSmall, color = Color.White, fontWeight = FontWeight.SemiBold)
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(15.dp))
                    .background(colors.accentSoft)
                    .clickable { showImportLinkDialog = true }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.Link, contentDescription = null, tint = colors.accent, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("Pegar DOI / URL", style = MaterialTheme.typography.labelSmall, color = colors.accent)
            }
            PaperFilterChip("Solo descargables", onlyOa) {
                onlyOa = !onlyOa
                if (query.isNotBlank()) onSearch(query, onlyOa, sourceFilter, sortCited)
            }
            PaperFilterChip("Más citados", sortCited) {
                sortCited = !sortCited
                if (query.isNotBlank()) onSearch(query, onlyOa, sourceFilter, sortCited)
            }
        }

        // Búsquedas sugeridas por el Docente según la lectura actual
        if (uiState.suggestedPaperQueries.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Sugerido:", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
                uiState.suggestedPaperQueries.forEach { sq ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.accentSoft)
                            .clickable {
                                query = sq
                                onSearch(sq, onlyOa, sourceFilter, sortCited)
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(sq, style = MaterialTheme.typography.labelSmall, color = colors.accent)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        if (uiState.isImportingDocument) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = colors.accent)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = uiState.importStatusText.ifBlank { "Importando documento académico..." },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary
                )
            }
            HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
        }

        if (uiState.isSearchingPapers) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.accent)
            }
        } else if (uiState.paperResults.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Busca fuentes académicas 100% corroborables:\n• Artículos con DOI verificado (DOAJ arbitrado, CrossRef, Europe PMC, OpenAlex, arXiv + Unpaywall)\n• Libros gratuitos descargables en EPUB/TXT (Project Gutenberg, Open Library, Internet Archive)\n• Búsqueda dedicada por Autor y enlace directo a Google Scholar.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
            ) {
                items(uiState.paperResults, key = { it.id }) { paper ->
                    PaperResultRow(
                        paper = paper,
                        isDownloading = uiState.downloadingPaperId == paper.id,
                        contextDocId = contextDocId,
                        onImportPdf = { onImportPdf(paper) },
                        onStudyAbstract = { onStudyAbstract(paper) },
                        onCompare = { docId -> onCompareInChat(paper, docId) },
                        onCite = { paperForCiteDialog = paper },
                        onOpenWeb = { url -> onOpenWebUrl(url) }
                    )
                    HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
                }
            }
        }
    }

    if (showImportLinkDialog) {
        var linkText by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showImportLinkDialog = false },
            containerColor = colors.surface,
            title = { Text("Importar desde Scholar / DOI / PDF", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    label = { Text("DOI, enlace arXiv o URL de PDF") },
                    placeholder = { Text("10.1038/... o https://...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportLinkDialog = false
                    onImportUrlOrDoi(linkText)
                }) { Text("Importar", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showImportLinkDialog = false }) { Text("Cancelar") }
            }
        )
    }

    paperForCiteDialog?.let { p ->
        val apa = remember(p.id) { onFormatApa(p) }
        val bib = remember(p.id) { onFormatBibTex(p) }
        AlertDialog(
            onDismissRequest = { paperForCiteDialog = null },
            containerColor = colors.surface,
            title = { Text("Citar artículo (APA 7 / BibTeX)", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("APA 7:", style = MaterialTheme.typography.labelSmall, color = colors.accent, fontWeight = FontWeight.Bold)
                    Text(apa, style = MaterialTheme.typography.bodySmall, color = colors.textPrimary)
                    Text("BibTeX:", style = MaterialTheme.typography.labelSmall, color = colors.accent, fontWeight = FontWeight.Bold)
                    Text(bib, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Cita APA", apa))
                    onShowMessage("Cita APA 7 copiada.")
                    paperForCiteDialog = null
                }) { Text("Copiar APA", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    cm.setPrimaryClip(ClipData.newPlainText("Cita BibTeX", bib))
                    onShowMessage("Cita BibTeX copiada.")
                    paperForCiteDialog = null
                }) { Text("Copiar BibTeX", color = colors.textSecondary) }
            }
        )
    }
}

@Composable
private fun PaperFilterChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = LocalCatedraColors.current
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(15.dp))
            .background(if (selected) colors.accentSoft else Color.Transparent)
            .border(0.5.dp, if (selected) colors.accent else colors.divider, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) colors.accent else colors.textSecondary
        )
    }
}

@Composable
private fun PaperResultRow(
    paper: PaperResult,
    isDownloading: Boolean,
    contextDocId: String?,
    onImportPdf: () -> Unit,
    onStudyAbstract: () -> Unit,
    onCompare: (String) -> Unit,
    onCite: () -> Unit,
    onOpenWeb: (String) -> Unit
) {
    val colors = LocalCatedraColors.current
    var expanded by remember(paper.id) { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (paper.verificationBadge.isNotBlank()) {
                Text(
                    text = paper.verificationBadge.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (paper.hasDirectDownload) colors.success else colors.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            } else {
                Text(
                    text = paper.source,
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.accent,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (paper.year != null) {
                Text("${paper.year}", style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
            if (paper.citationCount > 0) {
                val metric = if (paper.source == "Gutenberg") "${paper.citationCount} desc." else "${paper.citationCount} citas"
                Text(metric, style = MaterialTheme.typography.labelSmall, color = colors.textMuted)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = paper.title,
            style = MaterialTheme.typography.titleSmall,
            color = colors.textPrimary
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = "${paper.authors} · ${paper.venue}",
            style = MaterialTheme.typography.bodySmall,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (!paper.doi.isNullOrBlank()) {
            Text(
                text = "DOI: ${paper.doi.removePrefix("https://doi.org/")}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.citationText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        } else if (!paper.isbn.isNullOrBlank()) {
            Text(
                text = "ISBN: ${paper.isbn}",
                style = MaterialTheme.typography.labelSmall,
                color = colors.citationText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (paper.abstractText.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = paper.abstractText,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                maxLines = if (expanded) 12 else 3,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            val primaryImportLabel = when {
                !paper.openAccessEpubUrl.isNullOrBlank() -> "Importar Libro (EPUB)"
                !paper.openAccessTxtUrl.isNullOrBlank() -> "Importar Libro (TXT)"
                !paper.openAccessPdfUrl.isNullOrBlank() -> "Importar PDF"
                paper.hasVerifiedDoi -> "Buscar PDF (Unpaywall)"
                else -> null
            }
            if (primaryImportLabel != null) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accent)
                        .clickable(enabled = !isDownloading, onClick = onImportPdf)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(13.dp), strokeWidth = 2.dp, color = Color.White)
                    } else {
                        Icon(Icons.Rounded.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    }
                    Spacer(Modifier.width(5.dp))
                    Text(primaryImportLabel, style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .clickable(onClick = onStudyAbstract)
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.AutoMirrored.Rounded.MenuBook, contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(5.dp))
                Text("Estudiar resumen", style = MaterialTheme.typography.labelSmall, color = colors.textPrimary)
            }
            if (!contextDocId.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.accentSoft)
                        .clickable { onCompare(contextDocId) }
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.AutoMirrored.Rounded.CompareArrows, contentDescription = null, tint = colors.accent, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Comparar en chat", style = MaterialTheme.typography.labelSmall, color = colors.accent)
                }
            }
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceElevated)
                    .clickable(onClick = onCite)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.FormatQuote, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(4.dp))
                Text("Citar", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
            val webLink = paper.landingPageUrl ?: paper.doi
            if (!webLink.isNullOrBlank()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceElevated)
                        .clickable { onOpenWeb(webLink) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("DOI", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
                }
            }
        }
    }
}

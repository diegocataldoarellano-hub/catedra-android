package com.catedra.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoStories
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catedra.app.data.local.formatCompactSize
import com.catedra.app.data.model.StudyDocument
import com.catedra.app.ui.CatedraUiState
import com.catedra.app.ui.components.DocAvatar
import com.catedra.app.ui.components.formatRelativeTime
import com.catedra.app.ui.theme.LocalCatedraColors
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    uiState: CatedraUiState,
    onSelectDocument: (StudyDocument) -> Unit,
    onFolderTabChange: (String) -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onPickFile: () -> Unit,
    onPickCameraPhoto: () -> Unit,
    onCreatePastedText: (String, String) -> Unit,
    onOpenPapers: () -> Unit,
    onOpenDrive: () -> Unit,
    onOpenSettings: () -> Unit,
    onTogglePin: (StudyDocument) -> Unit,
    onRenameDoc: (String, String) -> Unit,
    onDeleteDoc: (StudyDocument) -> Unit
) {
    val colors = LocalCatedraColors.current
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    var isSearchOpen by remember { mutableStateOf(false) }
    var showAddSheet by remember { mutableStateOf(false) }
    var showPasteDialog by remember { mutableStateOf(false) }
    var docForActions by remember { mutableStateOf<StudyDocument?>(null) }
    var docToRename by remember { mutableStateOf<StudyDocument?>(null) }

    val folderTabs = listOf("Todos", "PDF", "Docs", "Hojas", "Visual", "Papers", "Con notas")

    val filteredDocs = remember(uiState.documents, uiState.selectedFolderTab, uiState.homeSearchQuery) {
        uiState.documents.filter { doc ->
            val matchesTab = when (uiState.selectedFolderTab) {
                "Todos" -> true
                "Con notas" -> doc.noteCount > 0
                else -> doc.docType.folderGroup == uiState.selectedFolderTab
            }
            val q = uiState.homeSearchQuery.trim()
            val matchesSearch = q.isBlank() ||
                doc.title.contains(q, ignoreCase = true) ||
                doc.fileName.contains(q, ignoreCase = true) ||
                doc.lastMessagePreview.contains(q, ignoreCase = true)
            matchesTab && matchesSearch
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colors.surface,
                modifier = Modifier.width(295.dp)
            ) {
                // Cabecera minimalista estilo Telegram
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.accent.copy(alpha = if (colors.isDark) 0.16f else 0.10f))
                        .statusBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(54.dp)
                            .clip(CircleShape)
                            .background(colors.accent),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.AutoStories,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "Cátedra",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                    Spacer(Modifier.height(2.dp))
                    val engineSummary = buildString {
                        append(uiState.settings.provider.displayName)
                        if (uiState.settings.hasNvidiaSkillsReady() && uiState.settings.useNvidiaRerank) {
                            append(" · NeMo")
                        }
                    }
                    Text(
                        text = engineSummary,
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }

                Spacer(Modifier.height(8.dp))

                DrawerRow(Icons.Rounded.FolderOpen, "Abrir archivo (Celular / Drive)") {
                    scope.launch { drawerState.close() }
                    onPickFile()
                }
                DrawerRow(Icons.Rounded.School, "Buscar Papers (OpenAlex / arXiv)") {
                    scope.launch { drawerState.close() }
                    onOpenPapers()
                }
                DrawerRow(Icons.Rounded.Cloud, "Google Drive") {
                    scope.launch { drawerState.close() }
                    onOpenDrive()
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 0.5.dp, color = colors.divider)
                DrawerRow(Icons.Rounded.Settings, "Ajustes (IA, NVIDIA y Voz)") {
                    scope.launch { drawerState.close() }
                    onOpenSettings()
                }

                Spacer(Modifier.weight(1f))
                HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            scope.launch { drawerState.close() }
                            onOpenSettings()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Storage, contentDescription = null, tint = colors.textMuted, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "Notas .md: ${formatCompactSize(uiState.storageStats.notesBytes)} (${uiState.storageStats.notesFileCount} arch.)",
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.textSecondary
                        )
                        Text(
                            text = "Total app: ${formatCompactSize(uiState.storageStats.totalBytes)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.textMuted
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            containerColor = colors.background,
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { showAddSheet = true },
                    shape = CircleShape,
                    containerColor = colors.accent,
                    contentColor = Color.White,
                    modifier = Modifier.size(56.dp)
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = "Nueva lectura", modifier = Modifier.size(26.dp))
                }
            }
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top Bar minimalista estilo Telegram
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.topBar)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Rounded.Menu, contentDescription = "Menú", tint = colors.textPrimary)
                    }
                    if (isSearchOpen) {
                        TextField(
                            value = uiState.homeSearchQuery,
                            onValueChange = onSearchQueryChange,
                            placeholder = { Text("Buscar lectura o tema...", color = colors.textMuted) },
                            singleLine = true,
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
                        IconButton(onClick = {
                            onSearchQueryChange("")
                            isSearchOpen = false
                        }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Cerrar búsqueda", tint = colors.textSecondary)
                        }
                    } else {
                        Text(
                            text = "Cátedra",
                            style = MaterialTheme.typography.titleLarge,
                            color = colors.textPrimary,
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 6.dp)
                        )
                        IconButton(onClick = onOpenPapers) {
                            Icon(Icons.Rounded.School, contentDescription = "Papers", tint = colors.textSecondary)
                        }
                        IconButton(onClick = { isSearchOpen = true }) {
                            Icon(Icons.Rounded.Search, contentDescription = "Buscar", tint = colors.textSecondary)
                        }
                    }
                }

                // Carpetas horizontales estilo Telegram (ultra-limpias)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.topBar)
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    folderTabs.forEach { tab ->
                        val selected = tab == uiState.selectedFolderTab
                        Column(
                            modifier = Modifier
                                .clickable { onFolderTabChange(tab) }
                                .padding(top = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = tab,
                                style = MaterialTheme.typography.labelMedium,
                                fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (selected) colors.accent else colors.textSecondary,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            Box(
                                modifier = Modifier
                                    .width(if (selected) 28.dp else 0.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                    .background(if (selected) colors.accent else Color.Transparent)
                            )
                        }
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

                // Aviso discreto si aún no configuró ninguna API Key
                if (!uiState.settings.hasActiveKey() && !uiState.settings.hasNvidiaSkillsReady()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.accentSoft)
                            .clickable(onClick = onOpenSettings)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, tint = colors.accent, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = "Configura tu API Key (Gemini o NVIDIA NIM) en Ajustes para conversar con el Docente.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
                }

                // Indicador de importación en curso
                if (uiState.isImportingDocument) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(colors.surfaceElevated)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colors.accent
                        )
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = uiState.importStatusText.ifBlank { "Procesando documento..." },
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.textPrimary
                        )
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = colors.divider)
                }

                // Lista de lecturas estilo chats de Telegram
                if (filteredDocs.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(colors.accentSoft),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Rounded.AutoStories,
                                    contentDescription = null,
                                    tint = colors.accent,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = "Tu mesa de lectura está limpia",
                                style = MaterialTheme.typography.titleMedium,
                                color = colors.textPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = "Abre un PDF, Word, Excel, PowerPoint, EPUB o foto de apuntes desde tu celular o Google Drive, o busca un paper académico.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                            Spacer(Modifier.height(20.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.accent)
                                        .clickable(onClick = onPickFile)
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text("Abrir documento", color = Color.White, style = MaterialTheme.typography.labelMedium)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(colors.surfaceElevated)
                                        .clickable(onClick = onOpenPapers)
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text("Buscar papers", color = colors.textPrimary, style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filteredDocs, key = { it.id }) { doc ->
                            DocumentChatRow(
                                doc = doc,
                                onClick = { onSelectDocument(doc) },
                                onLongClick = { docForActions = doc }
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 74.dp),
                                thickness = 0.5.dp,
                                color = colors.divider
                            )
                        }
                        item { Spacer(Modifier.height(84.dp)) }
                    }
                }
            }
        }
    }

    // Bottom Sheet minimalista para agregar lectura
    if (showAddSheet) {
        ModalBottomSheet(
            onDismissRequest = { showAddSheet = false },
            containerColor = colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 28.dp)
            ) {
                Text(
                    text = "Nueva lectura",
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                )
                SheetActionItem(
                    icon = Icons.Rounded.FolderOpen,
                    title = "Abrir archivo del celular o Google Drive",
                    subtitle = "PDF, DOCX, DOC, XLSX, CSV, PPTX, EPUB, TXT o imágenes"
                ) {
                    showAddSheet = false
                    onPickFile()
                }
                SheetActionItem(
                    icon = Icons.Rounded.CameraAlt,
                    title = "Fotografiar apuntes, libro o pizarra",
                    subtitle = "Análisis visual multimodal con Gemini / NVIDIA VLM"
                ) {
                    showAddSheet = false
                    onPickCameraPhoto()
                }
                SheetActionItem(
                    icon = Icons.Rounded.School,
                    title = "Buscar paper académico (OpenAlex / arXiv)",
                    subtitle = "Importa PDFs científicos abiertos directo al chat"
                ) {
                    showAddSheet = false
                    onOpenPapers()
                }
                SheetActionItem(
                    icon = Icons.Rounded.TextSnippet,
                    title = "Pegar texto o lectura rápida",
                    subtitle = "Estudia un fragmento, artículo web o apuntes sueltos"
                ) {
                    showAddSheet = false
                    showPasteDialog = true
                }
                SheetActionItem(
                    icon = Icons.Rounded.Cloud,
                    title = "Explorar Google Drive",
                    subtitle = "Abre Google Docs, Sheets, Slides y PDFs de tu nube"
                ) {
                    showAddSheet = false
                    onOpenDrive()
                }
            }
        }
    }

    // Diálogo para pegar texto
    if (showPasteDialog) {
        var title by remember { mutableStateOf("") }
        var body by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPasteDialog = false },
            containerColor = colors.surface,
            title = { Text("Pegar texto de estudio", color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Título") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("Texto completo o apuntes") },
                        minLines = 5,
                        maxLines = 10,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showPasteDialog = false
                    onCreatePastedText(title, body)
                }) { Text("Estudiar", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { showPasteDialog = false }) { Text("Cancelar") }
            }
        )
    }

    // Menú contextual al mantener presionado un chat de lectura
    docForActions?.let { doc ->
        ModalBottomSheet(
            onDismissRequest = { docForActions = null },
            containerColor = colors.surface
        ) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp)
                )
                SheetActionItem(
                    icon = Icons.Rounded.PushPin,
                    title = if (doc.isPinned) "Desfijar lectura" else "Fijar arriba",
                    subtitle = "Mantener siempre al inicio de la lista"
                ) {
                    onTogglePin(doc)
                    docForActions = null
                }
                SheetActionItem(
                    icon = Icons.Rounded.Edit,
                    title = "Renombrar título",
                    subtitle = "Cambiar el nombre visible en Cátedra"
                ) {
                    docToRename = doc
                    docForActions = null
                }
                SheetActionItem(
                    icon = Icons.Rounded.DeleteOutline,
                    title = "Eliminar lectura y notas",
                    subtitle = "Libera el índice y conversación de este documento"
                ) {
                    onDeleteDoc(doc)
                    docForActions = null
                }
            }
        }
    }

    docToRename?.let { doc ->
        var newName by remember(doc.id) { mutableStateOf(doc.title) }
        AlertDialog(
            onDismissRequest = { docToRename = null },
            containerColor = colors.surface,
            title = { Text("Renombrar lectura", color = colors.textPrimary) },
            text = {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameDoc(doc.id, newName)
                    docToRename = null
                }) { Text("Guardar", color = colors.accent) }
            },
            dismissButton = {
                TextButton(onClick = { docToRename = null }) { Text("Cancelar") }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DocumentChatRow(
    doc: StudyDocument,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val colors = LocalCatedraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DocAvatar(docType = doc.docType, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = doc.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(6.dp))
                if (doc.isPinned) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = "Fijado",
                        tint = colors.textMuted,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    text = formatRelativeTime(doc.updatedAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.textMuted
                )
            }
            Spacer(Modifier.height(3.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = doc.lastMessagePreview.ifBlank { "${doc.totalSections} secciones · ${doc.fileName}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (doc.noteCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.accentSoft)
                            .padding(horizontal = 7.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${doc.noteCount} notas",
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.accent,
                            fontSize = 10.5.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DrawerRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val colors = LocalCatedraColors.current
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary) },
        selected = false,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null, tint = colors.textSecondary, modifier = Modifier.size(21.dp)) },
        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent),
        modifier = Modifier.padding(horizontal = 8.dp)
    )
}

@Composable
private fun SheetActionItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    val colors = LocalCatedraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(colors.accentSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(21.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
    }
}

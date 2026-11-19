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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Headphones
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Quiz
import androidx.compose.material.icons.rounded.School
import androidx.compose.material.icons.rounded.TableChart
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catedra.app.data.model.StudyDocument
import com.catedra.app.ui.CatedraUiState
import com.catedra.app.ui.theme.LocalCatedraColors

/**
 * Hub minimalista del Ecosistema Académico Google:
 * - NotebookLM (Audio-Resumen hablado nativo + paquete de fuente hacia Drive/portapapeles + abrir NotebookLM)
 * - Google Forms (Quiz diagnóstico interactivo + exportador Apps Script / forms.new + hojas de respuestas en Drive)
 * - Google Scholar (búsqueda directa en Custom Tab + importador de DOI / enlaces de Scholar / PDFs)
 */
@Composable
fun EcosystemHubScreen(
    uiState: CatedraUiState,
    onBack: () -> Unit,
    onOpenUrl: (String) -> Unit,
    onBuildNotebookLmBundle: (StudyDocument) -> String,
    onUploadNotebookLmToDrive: (StudyDocument) -> Unit,
    onPlayAudioOverview: (StudyDocument) -> Unit,
    onLaunchQuizForDocument: (StudyDocument) -> Unit,
    onOpenDriveForForms: () -> Unit,
    onSearchGoogleScholar: (String) -> Unit,
    onImportPaperLinkOrDoi: (String) -> Unit,
    onShowMessage: (String) -> Unit
) {
    val colors = LocalCatedraColors.current
    val context = LocalContext.current

    var selectedDocId by remember(uiState.activeDocument, uiState.documents) {
        mutableStateOf(uiState.activeDocument?.id ?: uiState.documents.firstOrNull()?.id)
    }
    val selectedDoc = uiState.documents.firstOrNull { it.id == selectedDocId }

    var scholarQuery by remember(selectedDoc?.id) { mutableStateOf(selectedDoc?.title?.take(60) ?: "") }
    var paperUrlOrDoi by remember { mutableStateOf("") }

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
                Text("NotebookLM · Forms · Scholar", style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text("Conexiones académicas gratuitas (\$0)", style = MaterialTheme.typography.labelSmall, color = colors.textSecondary)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Selector rápido de documento activo si hay documentos cargados
            if (uiState.documents.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "DOCUMENTO DE TRABAJO",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.accent,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        uiState.documents.take(10).forEach { doc ->
                            val sel = doc.id == selectedDocId
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (sel) colors.accent else colors.surfaceElevated)
                                    .clickable { selectedDocId = doc.id }
                                    .padding(horizontal = 12.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = doc.title.take(28),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (sel) Color.White else colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            // 1. NOTEBOOKLM
            HubCard(
                title = "NotebookLM",
                subtitle = "Envía tu lectura y tus notas críticas a NotebookLM vía Google Drive o portapapeles, o escucha un Audio-Resumen estilo NotebookLM aquí mismo."
            ) {
                if (selectedDoc != null) {
                    HubActionButton(
                        icon = Icons.Rounded.Headphones,
                        label = if (uiState.isTeacherThinking) "Generando Audio-Resumen..." else "Escuchar Audio-Resumen (estilo NotebookLM)",
                        primary = true,
                        loading = uiState.isTeacherThinking
                    ) {
                        onPlayAudioOverview(selectedDoc)
                    }
                    HubActionButton(
                        icon = Icons.Rounded.CloudUpload,
                        label = "Enviar paquete a Drive (Catedra_NotebookLM)"
                    ) {
                        onUploadNotebookLmToDrive(selectedDoc)
                    }
                    HubActionButton(
                        icon = Icons.Rounded.ContentCopy,
                        label = "Copiar fuente limpia + Abrir NotebookLM"
                    ) {
                        val bundle = onBuildNotebookLmBundle(selectedDoc)
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("Fuente NotebookLM - ${selectedDoc.title}", bundle))
                        onShowMessage("Fuente copiada. En NotebookLM toca '+ Agregar fuente' → 'Texto copiado'.")
                        onOpenUrl("https://notebooklm.google.com")
                    }
                } else {
                    HubActionButton(
                        icon = Icons.Rounded.OpenInBrowser,
                        label = "Abrir NotebookLM",
                        primary = true
                    ) {
                        onOpenUrl("https://notebooklm.google.com")
                    }
                }
            }

            // 2. GOOGLE FORMS (FORMULARIOS)
            HubCard(
                title = "Google Forms (Formularios)",
                subtitle = "Genera cuestionarios diagnósticos sobre tu documento, expórtalos a Google Forms autocalificables o analiza hojas de respuestas desde Google Drive."
            ) {
                if (selectedDoc != null) {
                    HubActionButton(
                        icon = Icons.Rounded.Quiz,
                        label = "Generar Quiz Diagnóstico + Exportar a Google Forms",
                        primary = true
                    ) {
                        onLaunchQuizForDocument(selectedDoc)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.weight(1f)) {
                        HubActionButton(
                            icon = Icons.Rounded.Description,
                            label = "Mis Formularios"
                        ) {
                            onOpenUrl("https://docs.google.com/forms")
                        }
                    }
                    Box(modifier = Modifier.weight(1f)) {
                        HubActionButton(
                            icon = Icons.Rounded.AutoAwesome,
                            label = "Nuevo (forms.new)"
                        ) {
                            onOpenUrl("https://forms.new")
                        }
                    }
                }
                HubActionButton(
                    icon = Icons.Rounded.TableChart,
                    label = "Analizar respuestas de Formularios en Google Drive"
                ) {
                    onOpenDriveForForms()
                }
            }

            // 3. GOOGLE SCHOLAR
            HubCard(
                title = "Google Scholar",
                subtitle = "Explora Google Scholar con tu propia sesión y biblioteca (sin bloqueos CAPTCHA) e importa cualquier DOI, enlace de arXiv o PDF directo a Cátedra."
            ) {
                OutlinedTextField(
                    value = scholarQuery,
                    onValueChange = { scholarQuery = it },
                    label = { Text("Buscar tema o autor en Google Scholar") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HubActionButton(
                    icon = Icons.Rounded.School,
                    label = "Buscar en Google Scholar",
                    primary = true
                ) {
                    onSearchGoogleScholar(scholarQuery)
                }
                HorizontalDivider(thickness = 0.5.dp, color = colors.divider, modifier = Modifier.padding(vertical = 4.dp))
                OutlinedTextField(
                    value = paperUrlOrDoi,
                    onValueChange = { paperUrlOrDoi = it },
                    label = { Text("Pegar DOI, URL de Scholar, arXiv o PDF") },
                    placeholder = { Text("ej. 10.1038/... o https://arxiv.org/abs/...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                HubActionButton(
                    icon = Icons.Rounded.Link,
                    label = "Importar Paper desde DOI / Enlace"
                ) {
                    if (paperUrlOrDoi.isNotBlank()) {
                        onImportPaperLinkOrDoi(paperUrlOrDoi)
                        paperUrlOrDoi = ""
                    } else {
                        onShowMessage("Pega un DOI o enlace de PDF / arXiv primero.")
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun HubCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    val colors = LocalCatedraColors.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(colors.surfaceElevated)
            .border(0.6.dp, colors.divider, RoundedCornerShape(16.dp))
            .padding(15.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
        Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        content()
    }
}

@Composable
private fun HubActionButton(
    icon: ImageVector,
    label: String,
    primary: Boolean = false,
    loading: Boolean = false,
    onClick: () -> Unit
) {
    val colors = LocalCatedraColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) colors.accent else colors.surface)
            .border(if (primary) 0.dp else 0.6.dp, if (primary) Color.Transparent else colors.divider, RoundedCornerShape(12.dp))
            .clickable(enabled = !loading, onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp),
                strokeWidth = 2.dp,
                color = if (primary) Color.White else colors.accent
            )
        } else {
            Icon(
                icon,
                contentDescription = null,
                tint = if (primary) Color.White else colors.accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(9.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (primary) Color.White else colors.textPrimary,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

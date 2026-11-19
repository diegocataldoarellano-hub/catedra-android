package com.catedra.app.ui.screens

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
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.catedra.app.data.ai.DocenteAiEngine
import com.catedra.app.data.local.formatCompactSize
import com.catedra.app.data.model.AiProvider
import com.catedra.app.data.model.AppSettings
import com.catedra.app.data.model.AppThemeMode
import com.catedra.app.data.model.TeacherTone
import com.catedra.app.ui.CatedraUiState
import com.catedra.app.ui.theme.LocalCatedraColors
import java.util.Locale

@Composable
fun SettingsScreen(
    uiState: CatedraUiState,
    onBack: () -> Unit,
    onSaveSettings: (AppSettings) -> Unit,
    onFetchNvidiaCatalog: () -> Unit,
    onTestConnection: () -> Unit,
    onCompactHistory: () -> Unit
) {
    val colors = LocalCatedraColors.current
    var s by remember(uiState.settings) { mutableStateOf(uiState.settings) }
    var showKeys by remember { mutableStateOf(false) }

    fun update(transform: (AppSettings) -> AppSettings) {
        val updated = transform(s)
        s = updated
        onSaveSettings(updated)
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
            Text(
                text = "Ajustes",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = { showKeys = !showKeys }) {
                Icon(
                    imageVector = if (showKeys) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = "Mostrar u ocultar claves",
                    tint = colors.textSecondary
                )
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. MOTOR PRINCIPAL DE IA
            SettingsSectionHeader("Motor Docente Principal")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AiProvider.entries.forEach { prov ->
                    val selected = s.provider == prov
                    val borderColor = when {
                        selected && prov == AiProvider.NVIDIA_NIM -> colors.nvidiaGreen
                        selected -> colors.accent
                        else -> colors.divider
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) colors.surfaceElevated else Color.Transparent)
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .clickable { update { it.copy(provider = prov) } }
                            .padding(horizontal = 14.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = prov.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                color = if (prov == AiProvider.NVIDIA_NIM && selected) colors.nvidiaGreen else colors.textPrimary
                            )
                            Text(
                                text = prov.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.textSecondary
                            )
                        }
                        if (selected) {
                            Text(
                                text = "ACTIVO",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (prov == AiProvider.NVIDIA_NIM) colors.nvidiaGreen else colors.accent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 2. BLOQUE ESPECIAL NVIDIA NIM + SKILLS (Nemotron, NeMo Rerank, Visión VLM)
            SettingsSectionHeader("NVIDIA NIM · Modelos y Skills", accentOverride = colors.nvidiaGreen)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.surfaceElevated)
                    .border(0.75.dp, colors.nvidiaGreen.copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Skills activas con tu clave NVIDIA NIM: Nemotron 3.5 Super VL 120B (Docente + Visión), Nemotron 3 Ultra 550B, Embeddings semánticos 2048d (nemotron-3-embed-1b) y Traductor académico en el Lector (Riva Translate 4B).",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )

                OutlinedTextField(
                    value = s.nvidiaApiKey,
                    onValueChange = { v -> update { it.copy(nvidiaApiKey = v) } },
                    label = { Text("NVIDIA API Key (nvapi-...)") },
                    singleLine = true,
                    visualTransformation = if (showKeys) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = s.nvidiaModel,
                    onValueChange = { v -> update { it.copy(nvidiaModel = v) } },
                    label = { Text("Modelo Docente NVIDIA (Chat / Razonamiento)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Chips rápidos de modelos curados + catálogo en vivo
                val nvidiaChoices = (DocenteAiEngine.CURATED_NVIDIA_CHAT_MODELS + uiState.nvidiaCatalogModels.take(15)).distinct()
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    nvidiaChoices.forEach { m ->
                        val sel = s.nvidiaModel == m
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (sel) colors.nvidiaGreen else colors.surface)
                                .border(0.5.dp, colors.divider, RoundedCornerShape(12.dp))
                                .clickable { update { it.copy(nvidiaModel = m) } }
                                .padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = m.substringAfterLast('/'),
                                style = MaterialTheme.typography.labelSmall,
                                color = if (sel) Color.White else colors.textSecondary
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surface)
                        .border(0.5.dp, colors.nvidiaGreen.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                        .clickable(enabled = !uiState.isLoadingNvidiaCatalog, onClick = onFetchNvidiaCatalog)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (uiState.isLoadingNvidiaCatalog) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp, color = colors.nvidiaGreen)
                    } else {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, tint = colors.nvidiaGreen, modifier = Modifier.size(16.dp))
                    }
                    Spacer(Modifier.width(6.dp))
                    Text("Cargar catálogo en vivo de NVIDIA NIM", style = MaterialTheme.typography.labelSmall, color = colors.nvidiaGreen)
                }

                OutlinedTextField(
                    value = s.nvidiaVisionModel,
                    onValueChange = { v -> update { it.copy(nvidiaVisionModel = v) } },
                    label = { Text("Modelo Visión NVIDIA (Gráficos / PDFs escaneados)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                ToggleRow(
                    title = "Skill NVIDIA Embeddings Semánticos 2048d",
                    subtitle = "Usa nvidia/nemotron-3-embed-1b para hallar los pasajes exactos en libros extensos y mejorar las citas",
                    checked = s.useNvidiaRerank,
                    accent = colors.nvidiaGreen,
                    onCheckedChange = { c -> update { it.copy(useNvidiaRerank = c) } }
                )

                ToggleRow(
                    title = "Skill Visión Multimodal NVIDIA VLM",
                    subtitle = "Usa NVIDIA VLM para leer tablas, fórmulas, diagramas y fotos de apuntes",
                    checked = s.useNvidiaVisionSkill,
                    accent = colors.nvidiaGreen,
                    onCheckedChange = { c -> update { it.copy(useNvidiaVisionSkill = c) } }
                )

                ToggleRow(
                    title = "Mostrar razonamiento interno (Nemotron / R1)",
                    subtitle = "Muestra desplegado el proceso analítico del docente en cada respuesta",
                    checked = s.showReasoningSummary,
                    accent = colors.nvidiaGreen,
                    onCheckedChange = { c -> update { it.copy(showReasoningSummary = c) } }
                )
            }

            // 3. GOOGLE GEMINI API
            SettingsSectionHeader("Google Gemini API (1M tokens)")
            OutlinedTextField(
                value = s.geminiApiKey,
                onValueChange = { v -> update { it.copy(geminiApiKey = v) } },
                label = { Text("Gemini API Key (AI Studio)") },
                singleLine = true,
                visualTransformation = if (showKeys) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DocenteAiEngine.CURATED_GEMINI_MODELS.forEach { gm ->
                    val sel = s.geminiModel == gm
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (sel) colors.accent else colors.surfaceElevated)
                            .clickable { update { it.copy(geminiModel = gm) } }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(gm, style = MaterialTheme.typography.labelSmall, color = if (sel) Color.White else colors.textSecondary)
                    }
                }
            }

            // 4. OPENAI / OPENROUTER / COMPATIBLE
            SettingsSectionHeader("OpenAI / OpenRouter / Servidor Propio")
            OutlinedTextField(
                value = s.openAiBaseUrl,
                onValueChange = { v -> update { it.copy(openAiBaseUrl = v) } },
                label = { Text("Base URL (ej. https://openrouter.ai/api/v1)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.openAiApiKey,
                onValueChange = { v -> update { it.copy(openAiApiKey = v) } },
                label = { Text("API Key OpenAI / OpenRouter") },
                singleLine = true,
                visualTransformation = if (showKeys) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = s.openAiModel,
                onValueChange = { v -> update { it.copy(openAiModel = v) } },
                label = { Text("Modelo (ej. gpt-4o-mini, anthropic/claude-3.5-sonnet)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Botón probar conexión
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.accent)
                    .clickable(enabled = !uiState.isTestingConnection, onClick = onTestConnection)
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.isTestingConnection) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                } else {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(8.dp))
                Text("Probar conexión con el Docente", style = MaterialTheme.typography.labelMedium, color = Color.White)
            }
            if (!uiState.connectionTestResult.isNullOrBlank()) {
                val ok = uiState.connectionTestResult.startsWith("OK")
                Text(
                    text = uiState.connectionTestResult,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) colors.success else colors.danger
                )
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

            // 5. ESTILO PEDAGÓGICO DEL DOCENTE
            SettingsSectionHeader("Estilo del Docente")
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                TeacherTone.entries.forEach { tone ->
                    val sel = s.teacherTone == tone
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (sel) colors.accentSoft else colors.surfaceElevated)
                            .clickable { update { it.copy(teacherTone = tone) } }
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(tone.label, style = MaterialTheme.typography.titleSmall, color = colors.textPrimary)
                            Text(tone.description, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                        }
                    }
                }
            }

            // 6. VOZ Y LECTURA EN VOZ ALTA
            SettingsSectionHeader("Lectura en Voz Alta y Diálogo Oral")
            Text(
                text = "Velocidad de lectura: ${String.format(Locale.US, "%.2fx", s.speechRate)}",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textPrimary
            )
            Slider(
                value = s.speechRate,
                onValueChange = { r -> update { it.copy(speechRate = r) } },
                valueRange = 0.7f..2.0f,
                colors = SliderDefaults.colors(thumbColor = colors.accent, activeTrackColor = colors.accent)
            )
            ToggleRow(
                title = "Leer en voz alta respuestas del Docente",
                subtitle = "El docente responderá hablado automáticamente en el chat",
                checked = s.autoReadTeacherReplies,
                accent = colors.accent,
                onCheckedChange = { c -> update { it.copy(autoReadTeacherReplies = c) } }
            )

            // 7. GOOGLE DRIVE API + PUENTE RENDER OAUTH2
            SettingsSectionHeader("Google Drive (OAuth2 + Puente Render)")
            Text(
                text = "Abrir archivos desde Google Drive ya funciona al instante usando Android SAF. Para conectar tu cuenta en 1 toque con renovación automática y respaldo de notas .md, pega la URL de tu servidor Cátedra en Render:",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
            OutlinedTextField(
                value = s.driveBridgeUrl,
                onValueChange = { v -> update { it.copy(driveBridgeUrl = v) } },
                label = { Text("URL Puente Render (ej. https://catedra-portal.onrender.com)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (s.driveUserEmail.isNotBlank()) {
                Text(
                    text = "Cuenta vinculada: ${s.driveUserEmail}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.success
                )
            }
            OutlinedTextField(
                value = s.driveAccessToken,
                onValueChange = { v -> update { it.copy(driveAccessToken = v) } },
                label = { Text("Token manual OAuth (opcional si no usas Render)") },
                singleLine = true,
                visualTransformation = if (showKeys) VisualTransformation.None else PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            // 8. TEMA MINIMALISTA
            SettingsSectionHeader("Apariencia Telegram")
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AppThemeMode.entries.forEach { mode ->
                    val sel = s.themeMode == mode
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (sel) colors.accent else colors.surfaceElevated)
                            .clickable { update { it.copy(themeMode = mode) } }
                            .padding(horizontal = 13.dp, vertical = 7.dp)
                    ) {
                        Text(mode.label, style = MaterialTheme.typography.labelSmall, color = if (sel) Color.White else colors.textPrimary)
                    }
                }
            }

            // 9. ALMACENAMIENTO COMPACTO
            SettingsSectionHeader("Almacenamiento Ultraligero")
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceElevated)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Notas de estudio (.md/.txt): ${formatCompactSize(uiState.storageStats.notesBytes)} (${uiState.storageStats.notesFileCount} archivos)",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textPrimary
                )
                Text(
                    text = "Índices comprimidos (.gz): ${formatCompactSize(uiState.storageStats.compressedIndexBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Text(
                    text = "Base de chats SQLite: ${formatCompactSize(uiState.storageStats.databaseBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Text(
                    text = "Papers/Importaciones descargadas: ${formatCompactSize(uiState.storageStats.cachedPapersBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.accentSoft)
                        .clickable(onClick = onCompactHistory)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.CleaningServices, contentDescription = null, tint = colors.accent, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Compactar chats antiguos (conservar 100% notas .md)", style = MaterialTheme.typography.labelSmall, color = colors.accent)
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String, accentOverride: Color? = null) {
    val colors = LocalCatedraColors.current
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = accentOverride ?: colors.accent,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.6.sp
    )
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    accent: Color,
    onCheckedChange: (Boolean) -> Unit
) {
    val colors = LocalCatedraColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, color = colors.textPrimary, fontWeight = FontWeight.Medium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = accent)
        )
    }
}

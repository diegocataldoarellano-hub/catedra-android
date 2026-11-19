package com.catedra.app.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Login
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.catedra.app.data.local.formatCompactSize
import com.catedra.app.data.model.DocType
import com.catedra.app.data.model.DriveFileItem
import com.catedra.app.ui.CatedraUiState
import com.catedra.app.ui.components.DocAvatar
import com.catedra.app.ui.theme.LocalCatedraColors

@Composable
fun DriveBrowserScreen(
    uiState: CatedraUiState,
    onBack: () -> Unit,
    onOpenInstantSafPicker: () -> Unit,
    onConnectDriveOAuth: () -> Unit,
    onSyncAllNotesToDrive: () -> Unit,
    onDisconnectDrive: () -> Unit,
    onRefreshDrive: (String) -> Unit,
    onImportDriveFile: (DriveFileItem) -> Unit,
    onOpenGoogleForm: (DriveFileItem) -> Unit,
    onOpenSettings: () -> Unit
) {
    val colors = LocalCatedraColors.current
    var search by remember { mutableStateOf("") }
    val isConnected = uiState.settings.hasDriveConnected()
    val hasBridgeUrl = uiState.settings.driveBridgeUrl.startsWith("http")

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
                text = "Google Drive y Archivos",
                style = MaterialTheme.typography.titleMedium,
                color = colors.textPrimary,
                modifier = Modifier.weight(1f)
            )
            if (isConnected) {
                IconButton(onClick = { onRefreshDrive(search) }) {
                    Icon(Icons.Rounded.Refresh, contentDescription = "Actualizar", tint = colors.textSecondary)
                }
            }
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Ajustes Drive", tint = colors.textSecondary)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        // 1. Acceso instantáneo híbrido (Android SAF -> abre Google Drive y almacenamiento local sin OAuth)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(colors.accentSoft)
                .clickable(onClick = onOpenInstantSafPicker)
                .padding(13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.FolderOpen, contentDescription = null, tint = colors.accent, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Abrir desde Google Drive o Celular (Instantáneo)",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Usa el gestor nativo de Android para abrir cualquier PDF, Word, Excel, PPT o imagen sin configurar claves.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
            }
        }

        HorizontalDivider(thickness = 0.5.dp, color = colors.divider)

        // 2. Cuenta Google Drive conectada vía Render OAuth2 Bridge
        if (!isConnected) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Conexión directa con Google Drive (OAuth2)",
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.textPrimary
                )
                Text(
                    text = "Conecta tu cuenta en 1 toque mediante el puente Render de Cátedra para buscar carpetas aquí dentro, exportar Google Docs/Sheets automáticamente y respaldar todas tus notas .md en 'Catedra_Notas'.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary
                )
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.accent)
                            .clickable(onClick = onConnectDriveOAuth)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Login, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = if (hasBridgeUrl) "Conectar Google Drive" else "Configurar puente Render",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.White
                        )
                    }
                    if (hasBridgeUrl) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(18.dp))
                                .background(colors.surfaceElevated)
                                .clickable(onClick = onOpenSettings)
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text("Editar URL", style = MaterialTheme.typography.labelMedium, color = colors.textSecondary)
                        }
                    }
                }
            }
        } else {
            // Barra de cuenta conectada + respaldo total de notas
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.surfaceElevated)
                    .border(0.5.dp, colors.divider, RoundedCornerShape(12.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = colors.success, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = uiState.settings.driveUserEmail.ifBlank { "Google Drive conectado" },
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (uiState.settings.driveRefreshToken.isNotBlank()) "Sesión auto-renovable activa" else "Token temporal",
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = onSyncAllNotesToDrive, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Rounded.CloudUpload, contentDescription = "Respaldar todas las notas .md", tint = colors.accent, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = onDisconnectDrive, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = "Desconectar", tint = colors.textMuted, modifier = Modifier.size(19.dp))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text("Buscar en mi Google Drive...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { onRefreshDrive(search) }) {
                    Icon(Icons.Rounded.Search, contentDescription = "Buscar", tint = colors.accent)
                }
            }

            if (uiState.isLoadingDrive) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = colors.accent)
                }
            } else if (uiState.driveFiles.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No se encontraron documentos de estudio o toca actualizar arriba.",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(uiState.driveFiles, key = { it.id }) { item ->
                        val isForm = item.mimeType == "application/vnd.google-apps.form"
                        val docType = DocType.fromFileNameAndMime(item.name, item.mimeType)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (isForm) onOpenGoogleForm(item) else onImportDriveFile(item)
                                }
                                .padding(horizontal = 14.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            DocAvatar(docType = docType, size = 40.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = item.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = colors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val sizeStr = when {
                                    isForm -> "Google Form (abrir)"
                                    item.sizeBytes > 0 -> formatCompactSize(item.sizeBytes)
                                    else -> "Google Doc/Sheet"
                                }
                                Text(
                                    text = "$sizeStr · ${item.modifiedTime}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colors.textSecondary
                                )
                            }
                            Icon(Icons.Rounded.CloudDownload, contentDescription = "Importar", tint = colors.accent, modifier = Modifier.size(20.dp))
                        }
                        HorizontalDivider(modifier = Modifier.padding(start = 66.dp), thickness = 0.5.dp, color = colors.divider)
                    }
                }
            }
        }
    }
}

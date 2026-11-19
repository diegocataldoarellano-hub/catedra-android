package com.catedra.app.data.remote

import com.catedra.app.data.model.AppSettings
import com.catedra.app.data.model.DriveFileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

data class RefreshedDriveAuth(
    val accessToken: String,
    val expiresAtMs: Long
)

/**
 * Integración completa con Google Drive REST API v3 + Bridge OAuth2 en Render:
 * 1) Conexión en 1 toque vía OAuth2 (sin exponer Client Secret dentro del APK).
 * 2) Auto-renovación transparente de access_token usando refresh_token vía Render Bridge.
 * 3) Exploración y búsqueda de documentos (PDF, Docs, Sheets, Slides, Office, TXT, imágenes).
 * 4) Exportación automática de Google Docs -> .docx, Sheets -> .xlsx, Slides -> .pptx.
 * 5) Sincronización inteligente (crea o actualiza sin duplicar) de notas .md en 'Catedra_Notas'.
 */
class GoogleDriveService {

    private val client = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private val jsonMedia = "application/json; charset=utf-8".toMediaType()

    fun buildOAuthStartUrl(bridgeUrl: String): String {
        val base = bridgeUrl.trim().trimEnd('/')
        return "$base/auth/google/start?platform=android"
    }

    suspend fun ensureFreshToken(settings: AppSettings): Result<RefreshedDriveAuth> = withContext(Dispatchers.IO) {
        runCatching {
            val now = System.currentTimeMillis()
            val hasValidAccess = settings.driveAccessToken.isNotBlank() &&
                (settings.driveTokenExpiresAt == 0L || now < settings.driveTokenExpiresAt - 90_000L)
            if (hasValidAccess) {
                return@runCatching RefreshedDriveAuth(settings.driveAccessToken, settings.driveTokenExpiresAt)
            }
            if (settings.driveRefreshToken.isNotBlank() && settings.driveBridgeUrl.isNotBlank()) {
                return@runCatching refreshTokenViaBridge(settings.driveBridgeUrl, settings.driveRefreshToken).getOrThrow()
            }
            if (settings.driveAccessToken.isNotBlank()) {
                return@runCatching RefreshedDriveAuth(settings.driveAccessToken, settings.driveTokenExpiresAt)
            }
            error("Conecta Google Drive desde el botón superior o usa el selector instantáneo de Android.")
        }
    }

    suspend fun refreshTokenViaBridge(bridgeUrl: String, refreshToken: String): Result<RefreshedDriveAuth> =
        withContext(Dispatchers.IO) {
            runCatching {
                val base = bridgeUrl.trim().trimEnd('/')
                require(base.startsWith("http")) { "URL del puente Render inválida." }
                val payload = JSONObject().put("refresh_token", refreshToken.trim()).toString()
                val req = Request.Builder()
                    .url("$base/auth/google/refresh")
                    .post(payload.toRequestBody(jsonMedia))
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("No se pudo renovar sesión de Google Drive (HTTP ${resp.code}). Vuelve a conectar.")
                    }
                    val j = JSONObject(body)
                    val access = j.getString("access_token")
                    val expiresInSec = j.optLong("expires_in", 3500L)
                    RefreshedDriveAuth(
                        accessToken = access,
                        expiresAtMs = System.currentTimeMillis() + expiresInSec * 1000L
                    )
                }
            }
        }

    suspend fun fetchUserEmail(accessToken: String): String = withContext(Dispatchers.IO) {
        runCatching {
            val req = Request.Builder()
                .url("https://www.googleapis.com/drive/v3/about?fields=user(emailAddress)")
                .header("Authorization", "Bearer ${accessToken.trim()}")
                .get()
                .build()
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use ""
                JSONObject(resp.body?.string().orEmpty())
                    .optJSONObject("user")
                    ?.optString("emailAddress", "")
                    .orEmpty()
            }
        }.getOrDefault("")
    }

    suspend fun listStudyFiles(accessToken: String, searchQuery: String = ""): Result<List<DriveFileItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                require(accessToken.isNotBlank()) { "Conecta tu cuenta de Google Drive o usa el botón instantáneo." }
                val baseFilter = "trashed = false and (" +
                    "mimeType='application/pdf' or " +
                    "mimeType='application/vnd.google-apps.document' or " +
                    "mimeType='application/vnd.google-apps.spreadsheet' or " +
                    "mimeType='application/vnd.google-apps.presentation' or " +
                    "mimeType='application/vnd.google-apps.form' or " +
                    "mimeType='application/vnd.openxmlformats-officedocument.wordprocessingml.document' or " +
                    "mimeType='application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' or " +
                    "mimeType='application/vnd.openxmlformats-officedocument.presentationml.presentation' or " +
                    "mimeType='text/plain' or mimeType='text/markdown' or mimeType='text/csv' or " +
                    "mimeType contains 'image/')"
                val q = if (searchQuery.isNotBlank()) {
                    val escaped = searchQuery.replace("'", "\\'")
                    "name contains '$escaped' and $baseFilter"
                } else {
                    baseFilter
                }
                val encodedQ = URLEncoder.encode(q, "UTF-8")
                val fields = URLEncoder.encode("files(id,name,mimeType,size,modifiedTime)", "UTF-8")
                val url = "https://www.googleapis.com/drive/v3/files?q=$encodedQ&pageSize=35&orderBy=modifiedTime%20desc&fields=$fields"

                val req = Request.Builder()
                    .url(url)
                    .header("Authorization", "Bearer ${accessToken.trim()}")
                    .get()
                    .build()

                client.newCall(req).execute().use { resp ->
                    val body = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        error("Google Drive API HTTP ${resp.code}. Vuelve a conectar Google Drive.")
                    }
                    val arr = JSONObject(body).optJSONArray("files") ?: return@use emptyList()
                    val out = mutableListOf<DriveFileItem>()
                    for (i in 0 until arr.length()) {
                        val f = arr.getJSONObject(i)
                        val mime = f.optString("mimeType", "")
                        out.add(
                            DriveFileItem(
                                id = f.getString("id"),
                                name = f.optString("name", "Sin nombre"),
                                mimeType = mime,
                                sizeBytes = f.optString("size", "0").toLongOrNull() ?: 0L,
                                modifiedTime = f.optString("modifiedTime", "").take(10),
                                isFolder = mime == "application/vnd.google-apps.folder"
                            )
                        )
                    }
                    out
                }
            }
        }

    suspend fun downloadOrExportFile(
        accessToken: String,
        item: DriveFileItem,
        targetDir: File
    ): Result<Pair<File, String>> = withContext(Dispatchers.IO) {
        runCatching {
            val (downloadUrl, extension, resolvedMime) = when (item.mimeType) {
                "application/vnd.google-apps.document" -> Triple(
                    "https://www.googleapis.com/drive/v3/files/${item.id}/export?mimeType=application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                    ".docx",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
                )
                "application/vnd.google-apps.spreadsheet" -> Triple(
                    "https://www.googleapis.com/drive/v3/files/${item.id}/export?mimeType=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    ".xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                )
                "application/vnd.google-apps.presentation" -> Triple(
                    "https://www.googleapis.com/drive/v3/files/${item.id}/export?mimeType=application/vnd.openxmlformats-officedocument.presentationml.presentation",
                    ".pptx",
                    "application/vnd.openxmlformats-officedocument.presentationml.presentation"
                )
                else -> Triple(
                    "https://www.googleapis.com/drive/v3/files/${item.id}?alt=media",
                    "",
                    item.mimeType
                )
            }

            val safeName = item.name.replace(Regex("[^a-zA-Z0-9áéíóúñÁÉÍÓÚÑ ._\\-]"), "_")
            val finalName = if (extension.isNotEmpty() && !safeName.lowercase().endsWith(extension)) {
                "$safeName$extension"
            } else {
                safeName
            }
            val dest = File(targetDir, "${item.id.take(8)}_$finalName")

            val req = Request.Builder()
                .url(downloadUrl)
                .header("Authorization", "Bearer ${accessToken.trim()}")
                .get()
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("Error descargando desde Drive (HTTP ${resp.code})")
                val body = resp.body ?: error("Archivo vacío en Google Drive")
                FileOutputStream(dest).use { out -> body.byteStream().copyTo(out) }
            }
            dest to resolvedMime
        }
    }

    /**
     * Crea o actualiza (sin duplicar) una nota compacta .md dentro de la carpeta 'Catedra_Notas' en Google Drive.
     */
    suspend fun uploadMarkdownNote(
        accessToken: String,
        fileName: String,
        markdownContent: String,
        folderId: String? = null,
        folderName: String = "Catedra_Notas"
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            require(accessToken.isNotBlank()) { "Conecta tu cuenta de Google Drive primero." }
            val targetFolderId = folderId?.takeIf { it.isNotBlank() } ?: ensureNamedFolder(accessToken, folderName)
            val existingFileId = findExistingNoteId(accessToken, fileName, targetFolderId)

            if (existingFileId != null) {
                // Actualizar archivo existente sin crear copias duplicadas
                val updateReq = Request.Builder()
                    .url("https://www.googleapis.com/upload/drive/v3/files/$existingFileId?uploadType=media")
                    .header("Authorization", "Bearer ${accessToken.trim()}")
                    .patch(markdownContent.toRequestBody("text/markdown; charset=utf-8".toMediaType()))
                    .build()
                client.newCall(updateReq).execute().use { resp ->
                    if (!resp.isSuccessful) error("No se pudo actualizar la nota en Drive (HTTP ${resp.code})")
                }
                return@runCatching targetFolderId
            }

            val metadata = JSONObject().apply {
                put("name", fileName)
                put("mimeType", "text/markdown")
                if (targetFolderId.isNotBlank()) {
                    put("parents", JSONArray().put(targetFolderId))
                }
            }

            val body = MultipartBody.Builder()
                .setType("multipart/related".toMediaType())
                .addPart(metadata.toString().toRequestBody(jsonMedia))
                .addPart(markdownContent.toRequestBody("text/markdown; charset=utf-8".toMediaType()))
                .build()

            val req = Request.Builder()
                .url("https://www.googleapis.com/upload/drive/v3/files?uploadType=multipart")
                .header("Authorization", "Bearer ${accessToken.trim()}")
                .post(body)
                .build()

            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) error("No se pudo subir la nota a Drive (HTTP ${resp.code})")
                targetFolderId
            }
        }
    }

    private fun findExistingNoteId(accessToken: String, fileName: String, folderId: String): String? {
        if (folderId.isBlank()) return null
        val escaped = fileName.replace("'", "\\'")
        val q = URLEncoder.encode(
            "name='$escaped' and '$folderId' in parents and trashed=false",
            "UTF-8"
        )
        val req = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)")
            .header("Authorization", "Bearer ${accessToken.trim()}")
            .get()
            .build()
        return runCatching {
            client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return@use null
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                if (arr != null && arr.length() > 0) arr.getJSONObject(0).getString("id") else null
            }
        }.getOrNull()
    }

    private fun ensureNamedFolder(accessToken: String, folderName: String): String {
        val escaped = folderName.replace("'", "\\'")
        val q = URLEncoder.encode(
            "name='$escaped' and mimeType='application/vnd.google-apps.folder' and trashed=false",
            "UTF-8"
        )
        val searchReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files?q=$q&fields=files(id)")
            .header("Authorization", "Bearer ${accessToken.trim()}")
            .get()
            .build()

        client.newCall(searchReq).execute().use { resp ->
            if (resp.isSuccessful) {
                val arr = JSONObject(resp.body?.string().orEmpty()).optJSONArray("files")
                if (arr != null && arr.length() > 0) {
                    return arr.getJSONObject(0).getString("id")
                }
            }
        }

        val createBody = JSONObject().apply {
            put("name", folderName)
            put("mimeType", "application/vnd.google-apps.folder")
        }
        val createReq = Request.Builder()
            .url("https://www.googleapis.com/drive/v3/files")
            .header("Authorization", "Bearer ${accessToken.trim()}")
            .post(createBody.toString().toRequestBody(jsonMedia))
            .build()

        client.newCall(createReq).execute().use { resp ->
            if (!resp.isSuccessful) return ""
            return JSONObject(resp.body?.string().orEmpty()).optString("id", "")
        }
    }
}

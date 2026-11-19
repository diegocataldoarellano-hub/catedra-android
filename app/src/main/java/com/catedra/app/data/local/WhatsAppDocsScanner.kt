package com.catedra.app.data.local

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import androidx.documentfile.provider.DocumentFile
import com.catedra.app.data.model.DocType
import com.catedra.app.data.model.LocalDocEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Escáner estricto SOLO DE DOCUMENTOS para descargas de WhatsApp / WhatsApp Business
 * usando el marco oficial Android Storage Access Framework (SAF).
 * Excluye fotos, videos, audios, stickers, APKs y carpetas internas 'Sent'/'Private'.
 */
object WhatsAppDocsScanner {

    val ALLOWED_DOCUMENT_EXTENSIONS: Set<String> = setOf(
        "pdf", "docx", "doc", "xlsx", "xls", "csv", "tsv",
        "pptx", "ppt", "epub", "odt", "ods", "odp", "rtf",
        "txt", "md", "markdown", "html", "htm"
    )

    val DOCUMENT_ONLY_MIME_TYPES: Array<String> = arrayOf(
        "application/pdf",
        "application/msword",
        "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
        "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        "application/vnd.ms-powerpoint",
        "application/vnd.openxmlformats-officedocument.presentationml.presentation",
        "application/epub+zip",
        "application/vnd.oasis.opendocument.text",
        "application/vnd.oasis.opendocument.spreadsheet",
        "application/vnd.oasis.opendocument.presentation",
        "application/rtf",
        "text/rtf",
        "text/plain",
        "text/markdown",
        "text/csv",
        "text/tab-separated-values",
        "text/html"
    )

    private val CANDIDATE_RELATIVE_PATHS = listOf(
        "Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Documents",
        "Android/media/com.whatsapp.w4b/WhatsApp Business/Media/WhatsApp Business Documents",
        "WhatsApp/Media/WhatsApp Documents",
        "WhatsApp Business/Media/WhatsApp Business Documents",
        "Download"
    )

    fun isAllowedDocumentName(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in ALLOWED_DOCUMENT_EXTENSIONS
    }

    /**
     * Detecta la ruta real de Documentos de WhatsApp o WhatsApp Business existente en el equipo
     * y devuelve un Uri inicial compatible con DocumentsContract.EXTRA_INITIAL_URI.
     */
    fun detectInitialWhatsAppUri(): Pair<Uri, String> {
        val extRoot = Environment.getExternalStorageDirectory()
        val detectedRel = CANDIDATE_RELATIVE_PATHS.firstOrNull { rel ->
            val dir = File(extRoot, rel)
            dir.exists() && (dir.listFiles()?.isNotEmpty() == true)
        } ?: CANDIDATE_RELATIVE_PATHS.firstOrNull { rel ->
            File(extRoot, rel).exists()
        } ?: CANDIDATE_RELATIVE_PATHS.first()

        val encoded = Uri.encode(detectedRel)
        val initialUri = Uri.parse("content://com.android.externalstorage.documents/document/primary%3A$encoded")
        return initialUri to detectedRel
    }

    /**
     * Lista SOLO documentos válidos dentro del árbol SAF autorizado por el usuario,
     * ordenados del más reciente al más antiguo (ignorando subcarpetas 'Sent' y 'Private').
     */
    suspend fun listDocumentsInTree(
        context: Context,
        treeUriStr: String,
        includeSentFolder: Boolean = false
    ): Result<List<LocalDocEntry>> = withContext(Dispatchers.IO) {
        runCatching {
            val treeUri = Uri.parse(treeUriStr)
            val root = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Carpeta de WhatsApp no accesible. Vuelve a vincularla.")
            if (!root.canRead()) {
                error("Sin permiso de lectura en la carpeta seleccionada. Vuelve a vincularla.")
            }

            val results = mutableListOf<LocalDocEntry>()
            val children = root.listFiles()
            for (child in children) {
                val name = child.name ?: continue
                if (child.isDirectory) {
                    if (includeSentFolder && name.equals("Sent", ignoreCase = true)) {
                        for (sub in child.listFiles()) {
                            collectIfDocument(sub, results)
                        }
                    }
                    continue
                }
                collectIfDocument(child, results)
            }
            results.sortedByDescending { it.lastModified }
        }
    }

    private fun collectIfDocument(file: DocumentFile, out: MutableList<LocalDocEntry>) {
        if (!file.isFile) return
        val name = file.name ?: return
        if (!isAllowedDocumentName(name)) return
        val mime = file.type ?: "application/octet-stream"
        val docType = DocType.fromFileNameAndMime(name, mime)
        if (docType == DocType.IMAGE) return
        out.add(
            LocalDocEntry(
                uri = file.uri.toString(),
                name = name,
                docType = docType,
                mimeType = mime,
                sizeBytes = file.length(),
                lastModified = file.lastModified()
            )
        )
    }

    /**
     * Intenta leer directamente la ruta solo si el sistema permite lectura local
     * (Android <= 10 o si ya existe acceso); en Android 11+ normalmente devuelve vacío y usa SAF.
     */
    suspend fun tryDirectScanFallback(): List<LocalDocEntry> = withContext(Dispatchers.IO) {
        runCatching {
            val extRoot = Environment.getExternalStorageDirectory()
            val out = mutableListOf<LocalDocEntry>()
            for (rel in CANDIDATE_RELATIVE_PATHS.take(4)) {
                val dir = File(extRoot, rel)
                if (!dir.exists() || !dir.canRead()) continue
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && isAllowedDocumentName(f.name)) {
                        val dt = DocType.fromFileNameAndMime(f.name, null)
                        if (dt != DocType.IMAGE) {
                            out.add(
                                LocalDocEntry(
                                    uri = Uri.fromFile(f).toString(),
                                    name = f.name,
                                    docType = dt,
                                    mimeType = "application/octet-stream",
                                    sizeBytes = f.length(),
                                    lastModified = f.lastModified()
                                )
                            )
                        }
                    }
                }
            }
            out.sortedByDescending { it.lastModified }
        }.getOrDefault(emptyList())
    }
}

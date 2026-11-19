package com.catedra.app.data.local

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.catedra.app.data.model.DocSection
import com.catedra.app.data.model.NoteCategory
import com.catedra.app.data.model.StorageStats
import com.catedra.app.data.model.StudyDocument
import com.catedra.app.data.model.StudyNote
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * Almacenamiento ultraligero diseñado para gastar el mínimo espacio posible:
 * 1) Índice de texto completo del documento comprimido en GZIP (.sections.gz -> ~80% menos peso).
 * 2) Conocimiento aprendido, analizado y criticado guardado en archivos .md/.txt planos
 *    legibles por cualquier editor (típicamente 2 KB a 15 KB por libro completo).
 */
class CompactKnowledgeStore(private val context: Context) {

    private val indexDir: File = File(context.filesDir, "indices").apply { mkdirs() }
    private val notesDir: File = File(context.filesDir, "notas").apply { mkdirs() }
    val papersDir: File = File(context.filesDir, "papers").apply { mkdirs() }
    val importsDir: File = File(context.filesDir, "imports").apply { mkdirs() }

    private val dateFmt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun saveSectionsCompressed(documentId: String, sections: List<DocSection>) {
        val arr = JSONArray()
        for (s in sections) {
            arr.put(
                JSONObject().apply {
                    put("i", s.index)
                    put("l", s.label)
                    put("t", s.title)
                    put("c", s.content)
                    put("v", s.isVisualPage)
                }
            )
        }
        val outFile = File(indexDir, "$documentId.sections.gz")
        GZIPOutputStream(FileOutputStream(outFile)).bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(arr.toString())
        }
    }

    fun loadSections(documentId: String): List<DocSection> {
        val inFile = File(indexDir, "$documentId.sections.gz")
        if (!inFile.exists()) return emptyList()
        return runCatching {
            val jsonStr = GZIPInputStream(FileInputStream(inFile))
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val arr = JSONArray(jsonStr)
            val result = ArrayList<DocSection>(arr.length())
            for (idx in 0 until arr.length()) {
                val obj = arr.getJSONObject(idx)
                result.add(
                    DocSection(
                        index = obj.optInt("i", idx + 1),
                        label = obj.optString("l", "Sección ${idx + 1}"),
                        title = obj.optString("t", ""),
                        content = obj.optString("c", ""),
                        isVisualPage = obj.optBoolean("v", false)
                    )
                )
            }
            result
        }.getOrDefault(emptyList())
    }

    fun getCompressedIndexSizeBytes(documentId: String): Long {
        return File(indexDir, "$documentId.sections.gz").takeIf { it.exists() }?.length() ?: 0L
    }

    fun noteFileFor(doc: StudyDocument): File {
        val safeName = doc.title
            .lowercase()
            .replace(Regex("[^a-z0-9áéíóúñü]+"), "_")
            .trim('_')
            .take(42)
            .ifBlank { doc.id.take(8) }
        return File(notesDir, "${safeName}_${doc.id.take(6)}_notas.md")
    }

    /**
     * Genera y guarda el archivo plano .md/.txt compacto con TODO lo comprendido,
     * errores corregidos, análisis crítico, citas y notas de voz/texto.
     */
    fun writeMarkdownNotesFile(doc: StudyDocument, notes: List<StudyNote>): File {
        val content = buildCompactMarkdown(doc, notes)
        val file = noteFileFor(doc)
        file.writeText(content, Charsets.UTF_8)
        return file
    }

    fun buildCompactMarkdown(doc: StudyDocument, notes: List<StudyNote>): String {
        val sb = StringBuilder()
        sb.appendLine("# ${doc.title}")
        sb.appendLine("Archivo: ${doc.fileName} | Tipo: ${doc.docType.shortBadge} | Secciones: ${doc.totalSections}")
        if (!doc.paperAuthors.isNullOrBlank()) {
            sb.appendLine("Autores: ${doc.paperAuthors} (${doc.paperYear ?: ""})")
        }
        if (!doc.paperDoi.isNullOrBlank()) {
            sb.appendLine("DOI: ${doc.paperDoi}")
        }
        sb.appendLine("Actualizado: ${dateFmt.format(Date())}")
        sb.appendLine()

        if (notes.isEmpty()) {
            sb.appendLine("_Sin notas aún. Conversa con el Docente o dicta una nota de voz para registrar lo comprendido._")
            return sb.toString()
        }

        val orderedCategories = listOf(
            NoteCategory.COMPRENDIDO,
            NoteCategory.ERROR_CORREGIDO,
            NoteCategory.ANALISIS_CRITICA,
            NoteCategory.CITA,
            NoteCategory.DUDA,
            NoteCategory.NOTA_VOZ
        )

        for (cat in orderedCategories) {
            val items = notes.filter { it.category == cat }.sortedBy { it.createdAt }
            if (items.isEmpty()) continue
            sb.appendLine("## ${cat.mdHeader}")
            for (item in items) {
                val cite = if (item.citationRef.isNotBlank()) "[${item.citationRef}] " else ""
                val voiceTag = if (item.isFromVoice) "(voz) " else ""
                val date = dateFmt.format(Date(item.createdAt)).substring(5, 16)
                if (cat == NoteCategory.CITA) {
                    sb.appendLine("> $cite${item.content.trim()} _($date)_")
                } else {
                    sb.appendLine("- $cite$voiceTag${item.content.trim()} _($date)_")
                }
            }
            sb.appendLine()
        }
        return sb.toString()
    }

    fun getNoteFileSizeBytes(doc: StudyDocument): Long {
        return noteFileFor(doc).takeIf { it.exists() }?.length() ?: 0L
    }

    fun exportNoteToTxtTempFile(doc: StudyDocument, notes: List<StudyNote>, asTxt: Boolean = true): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val ext = if (asTxt) "txt" else "md"
        val safeTitle = doc.title.replace(Regex("[^a-zA-Z0-9áéíóúñÁÉÍÓÚÑ _-]"), "").trim().take(48).ifBlank { "notas_catedra" }
        val file = File(exportDir, "$safeTitle.$ext")
        file.writeText(buildCompactMarkdown(doc, notes), Charsets.UTF_8)
        return file
    }

    /**
     * Guarda una copia .txt o .md en una carpeta elegida por el usuario vía Android SAF
     * (por ejemplo, Documents/Catedra en el celular o una carpeta sincronizada).
     */
    fun exportNoteToSafTree(treeUriStr: String, doc: StudyDocument, notes: List<StudyNote>): Result<String> {
        return runCatching {
            val treeUri = Uri.parse(treeUriStr)
            val tree = DocumentFile.fromTreeUri(context, treeUri)
                ?: error("Carpeta de destino no accesible")
            val safeTitle = doc.title.replace(Regex("[^a-zA-Z0-9áéíóúñÁÉÍÓÚÑ _-]"), "").trim().take(48).ifBlank { "notas" }
            val fileName = "${safeTitle}_notas.txt"
            tree.findFile(fileName)?.delete()
            val target = tree.createFile("text/plain", fileName)
                ?: error("No se pudo crear el archivo en la carpeta seleccionada")
            val content = buildCompactMarkdown(doc, notes)
            context.contentResolver.openOutputStream(target.uri)?.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            } ?: error("No se pudo escribir en el archivo")
            fileName
        }
    }

    fun deleteDocumentFiles(doc: StudyDocument) {
        File(indexDir, "${doc.id}.sections.gz").delete()
        noteFileFor(doc).delete()
        doc.localCachedPath?.let { path ->
            val f = File(path)
            if (f.exists() && (f.parentFile == papersDir || f.parentFile == importsDir)) {
                f.delete()
            }
        }
    }

    fun getStorageStats(): StorageStats {
        val noteFiles = notesDir.listFiles()?.filter { it.isFile } ?: emptyList()
        val notesBytes = noteFiles.sumOf { it.length() }
        val indexBytes = indexDir.listFiles()?.sumOf { it.length() } ?: 0L
        val dbFile = context.getDatabasePath(CatedraDatabase.DB_NAME)
        val dbBytes = if (dbFile.exists()) dbFile.length() else 0L
        val papersBytes = (papersDir.listFiles()?.sumOf { it.length() } ?: 0L) +
            (importsDir.listFiles()?.sumOf { it.length() } ?: 0L)
        return StorageStats(
            notesBytes = notesBytes,
            notesFileCount = noteFiles.size,
            compressedIndexBytes = indexBytes,
            databaseBytes = dbBytes,
            cachedPapersBytes = papersBytes
        )
    }
}

fun formatCompactSize(bytes: Long): String {
    return when {
        bytes < 1024L -> "$bytes B"
        bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        else -> String.format(Locale.US, "%.2f MB", bytes / (1024.0 * 1024.0))
    }
}

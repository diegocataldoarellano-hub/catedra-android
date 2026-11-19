package com.catedra.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.catedra.app.data.model.ChatMessage
import com.catedra.app.data.model.DocType
import com.catedra.app.data.model.MessageRole
import com.catedra.app.data.model.NoteCategory
import com.catedra.app.data.model.StudyDocument
import com.catedra.app.data.model.StudyNote

class CatedraDatabase(context: Context) : SQLiteOpenHelper(context, DB_NAME, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "catedra_study.db"
        private const val DB_VERSION = 1
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE documents (
                id TEXT PRIMARY KEY,
                title TEXT NOT NULL,
                file_name TEXT NOT NULL,
                doc_type TEXT NOT NULL,
                source_uri TEXT NOT NULL,
                local_cached_path TEXT,
                mime_type TEXT NOT NULL,
                total_sections INTEGER NOT NULL DEFAULT 1,
                total_chars INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                last_message_preview TEXT NOT NULL DEFAULT '',
                is_pinned INTEGER NOT NULL DEFAULT 0,
                paper_doi TEXT,
                paper_authors TEXT,
                paper_year INTEGER,
                drive_file_id TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE messages (
                id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                reasoning TEXT,
                timestamp INTEGER NOT NULL,
                cited_labels TEXT NOT NULL DEFAULT '',
                is_voice_input INTEGER NOT NULL DEFAULT 0,
                attached_section_index INTEGER,
                is_saved_to_notes INTEGER NOT NULL DEFAULT 0,
                engine_badge TEXT,
                FOREIGN KEY(document_id) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_messages_doc_time ON messages(document_id, timestamp)")

        db.execSQL(
            """
            CREATE TABLE notes (
                id TEXT PRIMARY KEY,
                document_id TEXT NOT NULL,
                category TEXT NOT NULL,
                content TEXT NOT NULL,
                citation_ref TEXT NOT NULL DEFAULT '',
                is_from_voice INTEGER NOT NULL DEFAULT 0,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(document_id) REFERENCES documents(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX idx_notes_doc_time ON notes(document_id, created_at)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future migrations
    }

    fun upsertDocument(doc: StudyDocument) {
        val values = ContentValues().apply {
            put("id", doc.id)
            put("title", doc.title)
            put("file_name", doc.fileName)
            put("doc_type", doc.docType.name)
            put("source_uri", doc.sourceUri)
            put("local_cached_path", doc.localCachedPath)
            put("mime_type", doc.mimeType)
            put("total_sections", doc.totalSections)
            put("total_chars", doc.totalChars)
            put("created_at", doc.createdAt)
            put("updated_at", doc.updatedAt)
            put("last_message_preview", doc.lastMessagePreview)
            put("is_pinned", if (doc.isPinned) 1 else 0)
            put("paper_doi", doc.paperDoi)
            put("paper_authors", doc.paperAuthors)
            put("paper_year", doc.paperYear)
            put("drive_file_id", doc.driveFileId)
        }
        writableDatabase.insertWithOnConflict("documents", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getAllDocuments(): List<StudyDocument> {
        val list = mutableListOf<StudyDocument>()
        val sql = """
            SELECT d.*, (SELECT COUNT(*) FROM notes n WHERE n.document_id = d.id) AS note_cnt
            FROM documents d
            ORDER BY d.is_pinned DESC, d.updated_at DESC
        """.trimIndent()
        readableDatabase.rawQuery(sql, null).use { c ->
            while (c.moveToNext()) {
                val typeStr = c.getString(c.getColumnIndexOrThrow("doc_type"))
                val docType = runCatching { DocType.valueOf(typeStr) }.getOrDefault(DocType.UNKNOWN)
                val yearIdx = c.getColumnIndexOrThrow("paper_year")
                list.add(
                    StudyDocument(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        title = c.getString(c.getColumnIndexOrThrow("title")),
                        fileName = c.getString(c.getColumnIndexOrThrow("file_name")),
                        docType = docType,
                        sourceUri = c.getString(c.getColumnIndexOrThrow("source_uri")),
                        localCachedPath = c.getString(c.getColumnIndexOrThrow("local_cached_path")),
                        mimeType = c.getString(c.getColumnIndexOrThrow("mime_type")),
                        totalSections = c.getInt(c.getColumnIndexOrThrow("total_sections")),
                        totalChars = c.getInt(c.getColumnIndexOrThrow("total_chars")),
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at")),
                        updatedAt = c.getLong(c.getColumnIndexOrThrow("updated_at")),
                        lastMessagePreview = c.getString(c.getColumnIndexOrThrow("last_message_preview")) ?: "",
                        noteCount = c.getInt(c.getColumnIndexOrThrow("note_cnt")),
                        isPinned = c.getInt(c.getColumnIndexOrThrow("is_pinned")) == 1,
                        paperDoi = c.getString(c.getColumnIndexOrThrow("paper_doi")),
                        paperAuthors = c.getString(c.getColumnIndexOrThrow("paper_authors")),
                        paperYear = if (c.isNull(yearIdx)) null else c.getInt(yearIdx),
                        driveFileId = c.getString(c.getColumnIndexOrThrow("drive_file_id"))
                    )
                )
            }
        }
        return list
    }

    fun updateDocumentPreview(docId: String, preview: String, updatedAt: Long) {
        val values = ContentValues().apply {
            put("last_message_preview", preview.take(160))
            put("updated_at", updatedAt)
        }
        writableDatabase.update("documents", values, "id = ?", arrayOf(docId))
    }

    fun togglePinDocument(docId: String, pinned: Boolean) {
        val values = ContentValues().apply { put("is_pinned", if (pinned) 1 else 0) }
        writableDatabase.update("documents", values, "id = ?", arrayOf(docId))
    }

    fun renameDocument(docId: String, newTitle: String) {
        val values = ContentValues().apply { put("title", newTitle.trim()) }
        writableDatabase.update("documents", values, "id = ?", arrayOf(docId))
    }

    fun deleteDocument(docId: String) {
        writableDatabase.delete("documents", "id = ?", arrayOf(docId))
    }

    fun insertMessage(msg: ChatMessage) {
        val values = ContentValues().apply {
            put("id", msg.id)
            put("document_id", msg.documentId)
            put("role", msg.role.name)
            put("content", msg.content)
            put("reasoning", msg.reasoning)
            put("timestamp", msg.timestamp)
            put("cited_labels", msg.citedLabels.joinToString("|"))
            put("is_voice_input", if (msg.isVoiceInput) 1 else 0)
            if (msg.attachedSectionIndex != null) put("attached_section_index", msg.attachedSectionIndex)
            put("is_saved_to_notes", if (msg.isSavedToNotes) 1 else 0)
            put("engine_badge", msg.engineBadge)
        }
        writableDatabase.insertWithOnConflict("messages", null, values, SQLiteDatabase.CONFLICT_REPLACE)
        updateDocumentPreview(msg.documentId, msg.content.replace("\n", " "), msg.timestamp)
    }

    fun getMessages(documentId: String): List<ChatMessage> {
        val list = mutableListOf<ChatMessage>()
        readableDatabase.rawQuery(
            "SELECT * FROM messages WHERE document_id = ? ORDER BY timestamp ASC",
            arrayOf(documentId)
        ).use { c ->
            while (c.moveToNext()) {
                val roleStr = c.getString(c.getColumnIndexOrThrow("role"))
                val role = runCatching { MessageRole.valueOf(roleStr) }.getOrDefault(MessageRole.TEACHER)
                val citedRaw = c.getString(c.getColumnIndexOrThrow("cited_labels")) ?: ""
                val cited = if (citedRaw.isBlank()) emptyList() else citedRaw.split("|").filter { it.isNotBlank() }
                val secIdxCol = c.getColumnIndexOrThrow("attached_section_index")
                list.add(
                    ChatMessage(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        documentId = c.getString(c.getColumnIndexOrThrow("document_id")),
                        role = role,
                        content = c.getString(c.getColumnIndexOrThrow("content")),
                        reasoning = c.getString(c.getColumnIndexOrThrow("reasoning")),
                        timestamp = c.getLong(c.getColumnIndexOrThrow("timestamp")),
                        citedLabels = cited,
                        isVoiceInput = c.getInt(c.getColumnIndexOrThrow("is_voice_input")) == 1,
                        attachedSectionIndex = if (c.isNull(secIdxCol)) null else c.getInt(secIdxCol),
                        isSavedToNotes = c.getInt(c.getColumnIndexOrThrow("is_saved_to_notes")) == 1,
                        engineBadge = c.getString(c.getColumnIndexOrThrow("engine_badge"))
                    )
                )
            }
        }
        return list
    }

    fun markMessageSaved(messageId: String) {
        val values = ContentValues().apply { put("is_saved_to_notes", 1) }
        writableDatabase.update("messages", values, "id = ?", arrayOf(messageId))
    }

    /**
     * Compacta el historial crudo manteniendo solo los últimos N mensajes mientras
     * las notas en .md/.txt permanecen intactas — ahorra espacio en la base SQLite.
     */
    fun compactMessages(documentId: String? = null, keepLastPerDoc: Int = 8): Int {
        val db = writableDatabase
        val targetDocs = if (documentId != null) {
            listOf(documentId)
        } else {
            val ids = mutableListOf<String>()
            db.rawQuery("SELECT id FROM documents", null).use { c ->
                while (c.moveToNext()) ids.add(c.getString(0))
            }
            ids
        }
        var deletedTotal = 0
        for (docId in targetDocs) {
            deletedTotal += db.delete(
                "messages",
                "document_id = ? AND id NOT IN (SELECT id FROM messages WHERE document_id = ? ORDER BY timestamp DESC LIMIT ?)",
                arrayOf(docId, docId, keepLastPerDoc.toString())
            )
        }
        runCatching { db.execSQL("VACUUM") }
        return deletedTotal
    }

    fun insertNote(note: StudyNote) {
        val values = ContentValues().apply {
            put("id", note.id)
            put("document_id", note.documentId)
            put("category", note.category.name)
            put("content", note.content)
            put("citation_ref", note.citationRef)
            put("is_from_voice", if (note.isFromVoice) 1 else 0)
            put("created_at", note.createdAt)
        }
        writableDatabase.insertWithOnConflict("notes", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun getNotes(documentId: String): List<StudyNote> {
        val list = mutableListOf<StudyNote>()
        readableDatabase.rawQuery(
            "SELECT * FROM notes WHERE document_id = ? ORDER BY created_at DESC",
            arrayOf(documentId)
        ).use { c ->
            while (c.moveToNext()) {
                val catStr = c.getString(c.getColumnIndexOrThrow("category"))
                val cat = runCatching { NoteCategory.valueOf(catStr) }.getOrDefault(NoteCategory.NOTA_VOZ)
                list.add(
                    StudyNote(
                        id = c.getString(c.getColumnIndexOrThrow("id")),
                        documentId = c.getString(c.getColumnIndexOrThrow("document_id")),
                        category = cat,
                        content = c.getString(c.getColumnIndexOrThrow("content")),
                        citationRef = c.getString(c.getColumnIndexOrThrow("citation_ref")) ?: "",
                        isFromVoice = c.getInt(c.getColumnIndexOrThrow("is_from_voice")) == 1,
                        createdAt = c.getLong(c.getColumnIndexOrThrow("created_at"))
                    )
                )
            }
        }
        return list
    }

    fun getAllNotesCount(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM notes", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    fun deleteNote(noteId: String) {
        writableDatabase.delete("notes", "id = ?", arrayOf(noteId))
    }
}

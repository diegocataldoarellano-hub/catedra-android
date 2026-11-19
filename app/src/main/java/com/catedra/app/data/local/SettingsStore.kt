package com.catedra.app.data.local

import android.content.Context
import com.catedra.app.data.model.AiProvider
import com.catedra.app.data.model.AppSettings
import com.catedra.app.data.model.AppThemeMode
import com.catedra.app.data.model.TeacherTone

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("catedra_settings_v1", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val providerName = prefs.getString("provider", AiProvider.GEMINI.name) ?: AiProvider.GEMINI.name
        val provider = runCatching { AiProvider.valueOf(providerName) }.getOrDefault(AiProvider.GEMINI)
        val toneName = prefs.getString("teacher_tone", TeacherTone.SOCRATICO.name) ?: TeacherTone.SOCRATICO.name
        val tone = runCatching { TeacherTone.valueOf(toneName) }.getOrDefault(TeacherTone.SOCRATICO)
        val themeName = prefs.getString("theme_mode", AppThemeMode.SYSTEM.name) ?: AppThemeMode.SYSTEM.name
        val theme = runCatching { AppThemeMode.valueOf(themeName) }.getOrDefault(AppThemeMode.SYSTEM)

        return AppSettings(
            provider = provider,
            geminiApiKey = prefs.getString("gemini_key", "") ?: "",
            geminiModel = prefs.getString("gemini_model", "gemini-2.5-flash") ?: "gemini-2.5-flash",
            nvidiaApiKey = prefs.getString("nvidia_key", "") ?: "",
            nvidiaModel = prefs.getString("nvidia_model", "nvidia/llama-3.3-nemotron-super-49b-v1.5")
                ?: "nvidia/llama-3.3-nemotron-super-49b-v1.5",
            nvidiaVisionModel = prefs.getString("nvidia_vision_model", "nvidia/nemotron-3.5-super-vl-120b-a12b")
                ?: "nvidia/nemotron-3.5-super-vl-120b-a12b",
            useNvidiaRerank = prefs.getBoolean("use_nvidia_rerank", true),
            useNvidiaVisionSkill = prefs.getBoolean("use_nvidia_vision_skill", true),
            showReasoningSummary = prefs.getBoolean("show_reasoning", false),
            openAiApiKey = prefs.getString("openai_key", "") ?: "",
            openAiModel = prefs.getString("openai_model", "gpt-4o-mini") ?: "gpt-4o-mini",
            openAiBaseUrl = prefs.getString("openai_base_url", "https://api.openai.com/v1")
                ?: "https://api.openai.com/v1",
            teacherTone = tone,
            speechRate = prefs.getFloat("speech_rate", 1.05f),
            autoReadTeacherReplies = prefs.getBoolean("auto_read_replies", false),
            themeMode = theme,
            driveBridgeUrl = prefs.getString("drive_bridge_url", "") ?: "",
            driveAccessToken = prefs.getString("drive_access_token", "") ?: "",
            driveRefreshToken = prefs.getString("drive_refresh_token", "") ?: "",
            driveTokenExpiresAt = prefs.getLong("drive_token_expires_at", 0L),
            driveUserEmail = prefs.getString("drive_user_email", "") ?: "",
            driveNotesFolderId = prefs.getString("drive_notes_folder_id", "") ?: "",
            exportFolderUri = prefs.getString("export_folder_uri", "") ?: ""
        )
    }

    fun save(settings: AppSettings) {
        prefs.edit()
            .putString("provider", settings.provider.name)
            .putString("gemini_key", settings.geminiApiKey.trim())
            .putString("gemini_model", settings.geminiModel.trim())
            .putString("nvidia_key", settings.nvidiaApiKey.trim())
            .putString("nvidia_model", settings.nvidiaModel.trim())
            .putString("nvidia_vision_model", settings.nvidiaVisionModel.trim())
            .putBoolean("use_nvidia_rerank", settings.useNvidiaRerank)
            .putBoolean("use_nvidia_vision_skill", settings.useNvidiaVisionSkill)
            .putBoolean("show_reasoning", settings.showReasoningSummary)
            .putString("openai_key", settings.openAiApiKey.trim())
            .putString("openai_model", settings.openAiModel.trim())
            .putString("openai_base_url", settings.openAiBaseUrl.trim())
            .putString("teacher_tone", settings.teacherTone.name)
            .putFloat("speech_rate", settings.speechRate)
            .putBoolean("auto_read_replies", settings.autoReadTeacherReplies)
            .putString("theme_mode", settings.themeMode.name)
            .putString("drive_bridge_url", settings.driveBridgeUrl.trim().trimEnd('/'))
            .putString("drive_access_token", settings.driveAccessToken.trim())
            .putString("drive_refresh_token", settings.driveRefreshToken.trim())
            .putLong("drive_token_expires_at", settings.driveTokenExpiresAt)
            .putString("drive_user_email", settings.driveUserEmail.trim())
            .putString("drive_notes_folder_id", settings.driveNotesFolderId.trim())
            .putString("export_folder_uri", settings.exportFolderUri.trim())
            .apply()
    }
}

package com.brycewg.asrkb.store

/**
 * Prompt 预设的迁移/兼容逻辑（从 [Prefs] 中拆出）。
 *
 * 当前用途：
 * - 从旧版单一 `llmPrompt` 字段迁移为一个新的预设项
 */
internal object PromptPresetMigrations {
    fun migrateLegacyPromptIfNeeded(
        prefs: Prefs,
        current: List<PromptPreset>,
        legacyPrompt: String,
        initializedFromDefaults: Boolean
    ): List<PromptPreset> {
        if (legacyPrompt.isBlank()) return current
        if (current.any { it.content == legacyPrompt }) return current

        val migratedPreset = PromptPreset(
            id = java.util.UUID.randomUUID().toString(),
            title = "我的提示词",
            content = legacyPrompt
        )
        val updated = current + migratedPreset
        val shouldActivate = initializedFromDefaults ||
            prefs.activePromptId.isBlank() ||
            current.none { it.id == prefs.activePromptId } ||
            matchesDefaultPromptPresets(current)
        if (shouldActivate) {
            prefs.activePromptId = migratedPreset.id
        }
        prefs.setPromptPresets(updated)
        return updated
    }

    private fun matchesDefaultPromptPresets(presets: List<PromptPreset>): Boolean {
        val defaults = buildDefaultPromptPresets()
        if (presets.size != defaults.size) return false
        return presets.map { it.title to it.content } == defaults.map { it.title to it.content }
    }
}


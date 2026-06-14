package com.example.caraka.ui.util

import java.util.Locale

object SosAlertText {

    private val legacyPrefix = Regex("^\\[([^\\]]+)\\]\\s*(.*)$", RegexOption.IGNORE_CASE)

    /** User-facing headline for alert cards and previews. */
    fun headline(sosCategory: String?, rawContent: String): String {
        return extractUserMessage(rawContent) ?: defaultHeadline()
    }

    /** Short label for category chips (Medis, Keamanan, …). */
    fun categoryLabel(sosCategory: String?, rawContent: String = ""): String {
        return when (resolveCategory(sosCategory, rawContent)?.uppercase(Locale.ROOT)) {
            "MEDICAL" -> "Medis"
            "FIRE" -> "Kebakaran"
            "SECURITY" -> "Keamanan"
            "DISASTER" -> "Bencana"
            else -> ""
        }
    }

    fun defaultHeadline(): String = "SOS darurat"

    /** Content stored/transmitted for new SOS broadcasts (no bracket prefix). */
    fun storageContent(category: String, description: String): String {
        val trimmed = description.trim()
        return trimmed.ifBlank { defaultHeadline() }
    }

    fun resolveCategory(sosCategory: String?, rawContent: String): String? {
        if (!sosCategory.isNullOrBlank()) return sosCategory
        return legacyPrefix.find(rawContent.trim())?.groupValues?.getOrNull(1)?.trim()
    }

    private fun extractUserMessage(rawContent: String): String? {
        val trimmed = rawContent.trim()
        val body = legacyPrefix.find(trimmed)?.groupValues?.getOrNull(2)?.trim() ?: trimmed
        if (body.isBlank()) return null
        if (body.equals("EMERGENCY SOS!", ignoreCase = true)) return null
        if (body.equals(defaultHeadline(), ignoreCase = true)) return null
        return body
    }
}

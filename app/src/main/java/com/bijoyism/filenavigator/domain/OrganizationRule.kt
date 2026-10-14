package com.bijoyism.filenavigator.domain

import java.util.UUID

enum class ConflictPolicy {
    UNIQUE_NAME,
    SKIP,
    REPLACE,
}

data class OrganizationRule(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourceTreeUri: String = "",
    val destinationTreeUri: String = "",
    val extensions: Set<String> = emptySet(),
    val mimeTypes: Set<String> = emptySet(),
    val filenameKeywords: Set<String> = emptySet(),
    val minBytes: Long? = null,
    val maxBytes: Long? = null,
    val enabled: Boolean = true,
    val moveFiles: Boolean = true,
    val conflictPolicy: ConflictPolicy = ConflictPolicy.UNIQUE_NAME,
    val lastRunMillis: Long = 0L,
    val lastRunSummary: String = "Not run yet",
) {
    fun isValid(): String? = when {
        name.isBlank() -> "Give this rule a name."
        sourceTreeUri.isBlank() || destinationTreeUri.isBlank() -> "Choose both source and destination folders."
        sourceTreeUri == destinationTreeUri -> "Source and destination must be different folders."
        extensions.isEmpty() && mimeTypes.isEmpty() && filenameKeywords.isEmpty() ->
            "Add at least one extension, MIME type, or filename keyword."
        minBytes != null && minBytes < 0 -> "Minimum size cannot be negative."
        maxBytes != null && maxBytes < 0 -> "Maximum size cannot be negative."
        minBytes != null && maxBytes != null && minBytes > maxBytes ->
            "Minimum size cannot exceed maximum size."
        else -> null
    }
}

/** Pure matching logic, independent of Android APIs for reliable unit testing. */
object RuleMatcher {
    fun matches(fileName: String, mimeType: String?, rule: OrganizationRule): Boolean =
        matches(fileName, mimeType, null, rule)

    fun matches(
        fileName: String,
        mimeType: String?,
        sizeBytes: Long? = null,
        rule: OrganizationRule,
    ): Boolean {
        if (rule.extensions.isEmpty() && rule.mimeTypes.isEmpty() && rule.filenameKeywords.isEmpty()) {
            return false
        }
        if (rule.minBytes != null && (sizeBytes == null || sizeBytes < rule.minBytes)) return false
        if (rule.maxBytes != null && (sizeBytes == null || sizeBytes > rule.maxBytes)) return false

        val normalizedName = fileName.lowercase()
        val extension = normalizedName.substringAfterLast('.', missingDelimiterValue = "")
        val extensionMatch = rule.extensions.isEmpty() || rule.extensions.any {
            it.trim().removePrefix(".").lowercase() == extension
        }
        val normalizedMimeType = mimeType?.lowercase()
        val mimeMatch = rule.mimeTypes.isEmpty() || rule.mimeTypes.any { candidate ->
            val normalizedCandidate = candidate.trim().lowercase()
            if (normalizedCandidate.endsWith("/*")) {
                normalizedMimeType?.startsWith(normalizedCandidate.removeSuffix("*")) == true
            } else {
                normalizedMimeType == normalizedCandidate
            }
        }
        val keywordMatch = rule.filenameKeywords.isEmpty() || rule.filenameKeywords.any {
            normalizedName.contains(it.trim().lowercase())
        }
        return extensionMatch && mimeMatch && keywordMatch
    }
}

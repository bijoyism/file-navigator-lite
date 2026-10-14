package com.bijoyism.filenavigator.domain

/** Resolves a conflict without mutating or deleting destination content. */
object DestinationNameResolver {
    /** Returns null for SKIP, a safe name for UNIQUE_NAME, and refuses unsafe replacement. */
    fun resolve(originalName: String, existingNames: Set<String>, policy: ConflictPolicy): String? {
        val normalizedNames = existingNames.map { it.lowercase() }.toSet()
        if (originalName.lowercase() !in normalizedNames) return originalName
        return when (policy) {
            ConflictPolicy.SKIP -> null
            ConflictPolicy.REPLACE -> throw IllegalStateException(
                "Safe replacement is not supported by this storage provider. Choose unique-name or skip to protect existing data.",
            )
            ConflictPolicy.UNIQUE_NAME -> {
                val dot = originalName.lastIndexOf('.')
                val stem = if (dot > 0) originalName.substring(0, dot) else originalName
                val extension = if (dot > 0) originalName.substring(dot) else ""
                var suffix = 1
                var candidate: String
                do {
                    candidate = "$stem ($suffix)$extension"
                    suffix++
                } while (candidate.lowercase() in normalizedNames && suffix < 100_000)
                if (suffix >= 100_000) throw IllegalStateException("Couldn't find a unique destination name.")
                candidate
            }
        }
    }
}

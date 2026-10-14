package com.bijoyism.filenavigator.domain

data class ScanDiffResult(
    val initializedNow: Boolean,
    val newKeys: Set<String>,
    val savedKeys: Set<String>,
)

/** Deduplicates periodic scan notifications and establishes a no-notification baseline on first run. */
object ScanDiff {
    const val BASELINE_KEY = "__scan_initialized__"

    fun compare(previous: Set<String>, current: Set<String>): ScanDiffResult {
        if (BASELINE_KEY !in previous) {
            return ScanDiffResult(
                initializedNow = true,
                newKeys = emptySet(),
                savedKeys = current + BASELINE_KEY,
            )
        }
        val priorFiles = previous - BASELINE_KEY
        return ScanDiffResult(
            initializedNow = false,
            newKeys = current - priorFiles,
            savedKeys = priorFiles + current + BASELINE_KEY,
        )
    }
}

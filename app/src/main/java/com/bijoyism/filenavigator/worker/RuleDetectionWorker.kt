package com.bijoyism.filenavigator.worker

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bijoyism.filenavigator.data.AppDataRepository
import com.bijoyism.filenavigator.domain.RuleMatcher
import com.bijoyism.filenavigator.domain.ScanDiff
import com.bijoyism.filenavigator.notifications.RuleNotifications
import com.bijoyism.filenavigator.storage.StorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class RuleDetectionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val data = AppDataRepository(applicationContext)
        val settings = data.settings.first()
        val rules = data.rules.first().filter { it.enabled }
        val storage = StorageRepository(applicationContext)
        var completedRules = 0
        var failedRules = 0

        for (rule in rules) {
            try {
                val validationError = rule.isValid()
                if (validationError != null) {
                    data.updateRuleResult(rule.id, "Scan skipped: $validationError")
                    failedRules++
                    continue
                }
                val treeUri = Uri.parse(rule.sourceTreeUri)
                val root = storage.rootDocumentUri(treeUri)
                val files = storage.listDirectory(treeUri, root).filter { entry ->
                    !entry.isDirectory && RuleMatcher.matches(entry.name, entry.mimeType, entry.sizeBytes, rule)
                }
                val previous = data.seenFileKeys(rule.id)
                val keysByFile = files.associateBy { fileKey(it.uri, it.sizeBytes, it.lastModifiedMillis) }
                val diff = ScanDiff.compare(previous, keysByFile.keys)
                data.saveSeenFileKeys(rule.id, diff.savedKeys)
                val newFiles = diff.newKeys.mapNotNull { keysByFile[it] }
                if (!diff.initializedNow && settings.notificationsEnabled && !settings.quietMode) {
                    RuleNotifications.showMatches(applicationContext, rule, newFiles)
                }
                data.updateRuleResult(
                    rule.id,
                    if (diff.initializedNow) "Scan baseline saved (${files.size} matches)"
                    else "Scan complete (${files.size} matches, ${newFiles.size} new)",
                )
                completedRules++
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: SecurityException) {
                data.updateRuleResult(rule.id, "Scan failed: folder permission denied or revoked")
                failedRules++
            } catch (error: Exception) {
                data.updateRuleResult(rule.id, "Scan failed: ${error.message ?: error.javaClass.simpleName}")
                failedRules++
            }
        }

        return when {
            failedRules > 0 && completedRules == 0 && runAttemptCount < 2 -> Result.retry()
            failedRules > 0 && completedRules == 0 -> Result.failure()
            else -> Result.success()
        }
    }

    private fun fileKey(uri: String, size: Long, modified: Long): String = "$uri|$size|$modified"
}

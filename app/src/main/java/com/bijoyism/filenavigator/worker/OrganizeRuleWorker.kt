package com.bijoyism.filenavigator.worker

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.bijoyism.filenavigator.data.AppDataRepository
import com.bijoyism.filenavigator.data.OperationRecord
import com.bijoyism.filenavigator.domain.RuleMatcher
import com.bijoyism.filenavigator.storage.StorageRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

class OrganizeRuleWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val ruleId = inputData.getString(KEY_RULE_ID) ?: return Result.failure()
        val data = AppDataRepository(applicationContext)
        return try {
            val rule = data.rules.first().firstOrNull { it.id == ruleId } ?: return Result.failure()
            if (!rule.enabled) {
                data.updateRuleResult(rule.id, "Organization skipped: rule is disabled")
                return Result.failure()
            }
            val validationError = rule.isValid()
            if (validationError != null) {
                data.updateRuleResult(rule.id, "Organization skipped: $validationError")
                return Result.failure()
            }
            val storage = StorageRepository(applicationContext)
            val sourceTree = Uri.parse(rule.sourceTreeUri)
            val destinationTree = Uri.parse(rule.destinationTreeUri)
            val sourceRoot = storage.rootDocumentUri(sourceTree)
            val destinationRoot = storage.rootDocumentUri(destinationTree)
            if (sourceRoot.authority == destinationRoot.authority &&
                DocumentsContract.getDocumentId(sourceRoot) == DocumentsContract.getDocumentId(destinationRoot)
            ) {
                data.updateRuleResult(rule.id, "Organization skipped: source and destination are the same folder")
                return Result.failure()
            }
            val files = storage.listDirectory(sourceTree, sourceRoot).filter { entry ->
                !entry.isDirectory && RuleMatcher.matches(entry.name, entry.mimeType, entry.sizeBytes, rule)
            }
            var succeeded = 0
            var failed = 0
            files.forEach { entry ->
                try {
                    val result = if (rule.moveFiles) {
                        storage.move(sourceTree, entry, destinationTree, destinationRoot, rule.conflictPolicy)
                    } else {
                        storage.copy(sourceTree, entry, destinationTree, destinationRoot, rule.conflictPolicy)
                    }
                    data.addHistory(result.toHistoryRecord())
                    if (result.success) succeeded++ else failed++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (error: Exception) {
                    failed++
                    data.addHistory(
                        OperationRecord(
                            operation = if (rule.moveFiles) "MOVE" else "COPY",
                            name = entry.name,
                            sourceUri = entry.uri,
                            sourceParentUri = entry.parentUri,
                            destinationParentUri = destinationRoot.toString(),
                            success = false,
                            message = error.message ?: "Operation failed.",
                        ),
                    )
                }
            }
            data.updateRuleResult(rule.id, "$succeeded succeeded, $failed failed/skipped")
            Result.success()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            runCatching {
                data.updateRuleResult(ruleId, "Organization failed: ${error.message ?: error.javaClass.simpleName}")
            }
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }

    private fun com.bijoyism.filenavigator.storage.StorageOperationResult.toHistoryRecord() = OperationRecord(
        operation = operation,
        name = name,
        sourceUri = sourceUri,
        destinationUri = destinationUri,
        sourceParentUri = sourceParentUri,
        destinationParentUri = destinationParentUri,
        success = success,
        message = message,
        undoAction = undoAction,
    )

    companion object {
        const val KEY_RULE_ID = "rule_id"
    }
}

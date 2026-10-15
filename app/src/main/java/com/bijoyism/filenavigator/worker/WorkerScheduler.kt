package com.bijoyism.filenavigator.worker

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object WorkerScheduler {
    private const val SCAN_WORK_NAME = "file-navigator-rule-detection"

    fun updatePeriodicScanning(context: Context, enabled: Boolean) {
        val manager = WorkManager.getInstance(context)

        if (!enabled) {
            manager.cancelUniqueWork(SCAN_WORK_NAME)
            return
        }

        val request =
            PeriodicWorkRequestBuilder<RuleDetectionWorker>(
                15,
                TimeUnit.MINUTES,
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                        .build(),
                )
                .build()

        manager.enqueueUniquePeriodicWork(
            SCAN_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun scanNow(context: Context) {
        val request =
            OneTimeWorkRequestBuilder<RuleDetectionWorker>()
                .build()

        WorkManager.getInstance(context).enqueue(request)
    }

    fun organizeRule(context: Context, ruleId: String) {
        val inputData = workDataOf(
            OrganizeRuleWorker.KEY_RULE_ID to ruleId,
        )
        val request =
            OneTimeWorkRequestBuilder<OrganizeRuleWorker>()
                .setInputData(inputData)
                .build()

        WorkManager.getInstance(context).enqueue(request)
    }
}
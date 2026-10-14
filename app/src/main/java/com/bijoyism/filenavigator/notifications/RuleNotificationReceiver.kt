package com.bijoyism.filenavigator.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.bijoyism.filenavigator.worker.WorkerScheduler

class RuleNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val notificationId = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        when (intent.action) {
            ACTION_ORGANIZE -> intent.getStringExtra(EXTRA_RULE_ID)?.let { WorkerScheduler.organizeRule(context, it) }
            ACTION_DISMISS -> Unit
        }
        NotificationManagerCompat.from(context).cancel(notificationId)
    }

    companion object {
        const val ACTION_ORGANIZE = "com.bijoyism.filenavigator.action.ORGANIZE"
        const val ACTION_DISMISS = "com.bijoyism.filenavigator.action.DISMISS"
        const val EXTRA_RULE_ID = "rule_id"
        const val EXTRA_NOTIFICATION_ID = "notification_id"
    }
}

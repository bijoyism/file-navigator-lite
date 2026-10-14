package com.bijoyism.filenavigator.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.bijoyism.filenavigator.MainActivity
import com.bijoyism.filenavigator.R
import com.bijoyism.filenavigator.domain.OrganizationRule
import com.bijoyism.filenavigator.storage.BrowserEntry

object RuleNotifications {
    private const val CHANNEL_ID = "organization_matches"

    fun showMatches(context: Context, rule: OrganizationRule, matches: List<BrowserEntry>) {
        if (matches.isEmpty() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            notificationManager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Organization suggestions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "New files that match your organization rules"
                },
            )
        }
        val id = rule.id.hashCode()
        val organizeIntent = Intent(context, RuleNotificationReceiver::class.java).apply {
            action = RuleNotificationReceiver.ACTION_ORGANIZE
            putExtra(RuleNotificationReceiver.EXTRA_RULE_ID, rule.id)
            putExtra(RuleNotificationReceiver.EXTRA_NOTIFICATION_ID, id)
        }
        val dismissIntent = Intent(context, RuleNotificationReceiver::class.java).apply {
            action = RuleNotificationReceiver.ACTION_DISMISS
            putExtra(RuleNotificationReceiver.EXTRA_NOTIFICATION_ID, id)
        }
        val organizePending = PendingIntent.getBroadcast(
            context,
            id * 3 + 1,
            organizeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismissPending = PendingIntent.getBroadcast(
            context,
            id * 3 + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openApp = PendingIntent.getActivity(
            context,
            id * 3 + 3,
            Intent(context, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val reviewPending = PendingIntent.getActivity(
            context,
            id * 3 + 5,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_START_ROUTE, "automation")
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val openFile = matches.firstOrNull()?.let { entry ->
            PendingIntent.getActivity(
                context,
                id * 3 + 4,
                Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(Uri.parse(entry.uri), entry.mimeType ?: "*/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Files match '${rule.name}'")
            .setContentText("${matches.size} new file${if (matches.size == 1) "" else "s"} are ready to organize.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(matches.take(3).joinToString { it.name }))
            .setContentIntent(openApp)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .addAction(0, "Organize now", organizePending)
            .addAction(0, "Dismiss", dismissPending)
            .addAction(0, "Review destination", reviewPending)
        if (openFile != null) builder.addAction(0, "Open file", openFile)
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}

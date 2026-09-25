package com.example.relevantreasearchupdates.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.relevantreasearchupdates.MainActivity
import com.example.relevantreasearchupdates.data.Paper

object NotificationHelper {
    const val CHANNEL_ID = "new_papers"
    private const val GROUP_KEY = "new_papers_group"
    private const val SUMMARY_NOTIFICATION_ID = 1000

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "New literature",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "Alerts when a new paper matches one of your authors or keywords"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager?.createNotificationChannel(channel)
    }

    fun showNewPapers(context: Context, papers: List<Paper>) {
        if (papers.isEmpty()) return
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val inboxStyle = NotificationCompat.InboxStyle()
        papers.take(5).forEach { inboxStyle.addLine(it.title) }
        if (papers.size > 5) inboxStyle.setSummaryText("+${papers.size - 5} more")

        val title = if (papers.size == 1) "1 new paper found" else "${papers.size} new papers found"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(papers.first().title)
            .setStyle(inboxStyle)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .build()

        // Each check gets its own notification rather than reusing one fixed ID, so a later
        // check can't silently replace an earlier alert you haven't looked at yet. They're
        // bundled under one group summary so they stack instead of cluttering the shade.
        val manager = NotificationManagerCompat.from(context)
        manager.notify(nextNotificationId(), notification)

        val summary = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("New papers found")
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
            .build()
        manager.notify(SUMMARY_NOTIFICATION_ID, summary)
    }

    /** Distinct per notification; wraps within a range that can't collide with the summary ID. */
    private fun nextNotificationId(): Int =
        2000 + (System.currentTimeMillis() / 1000 % 1_000_000).toInt()
}

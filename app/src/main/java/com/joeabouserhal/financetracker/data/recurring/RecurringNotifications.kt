package com.joeabouserhal.financetracker.data.recurring

import android.app.NotificationChannel
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.joeabouserhal.financetracker.R

class RecurringNotifications(private val context: Context) {
  fun createChannel() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val channel = NotificationChannel(CHANNEL, "Recurring transactions", NotificationManager.IMPORTANCE_DEFAULT).apply { description = "Review recurring transactions when they are added." }
    context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
  }

  fun show(due: List<RecurringDue>) {
    if (due.isEmpty() || !NotificationManagerCompat.from(context).areNotificationsEnabled()) return
    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
    // Permission can be revoked between checking it and posting the notification.
    try {
    val openApp = PendingIntent.getActivity(context, SUMMARY_ID, Intent(context, com.joeabouserhal.financetracker.MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    if (due.size > 1) {
      NotificationManagerCompat.from(context).notify(SUMMARY_ID, NotificationCompat.Builder(context, CHANNEL)
        .setSmallIcon(R.drawable.ic_tab_transactions).setContentTitle("${due.size} recurring transactions added")
        .setContentText("Open Finance Tracker to review them.").setContentIntent(openApp).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).build())
      return
    }
    val item = due.single(); val id = item.occurrenceId.hashCode()
    val verify = PendingIntent.getBroadcast(context, id, Intent(context, RecurringActionReceiver::class.java).setAction(ACTION_VERIFY).putExtra(EXTRA_OCCURRENCE_ID, item.occurrenceId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    val snooze = PendingIntent.getActivity(context, id + 1, Intent(context, SnoozeRecurringActivity::class.java).putExtra(EXTRA_OCCURRENCE_ID, item.occurrenceId), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    NotificationManagerCompat.from(context).notify(id, NotificationCompat.Builder(context, CHANNEL)
      .setSmallIcon(R.drawable.ic_tab_transactions).setContentTitle("${item.presetName} added")
      .setContentText("${format(item.amount)} ${item.currencyCode} · Verify or snooze")
      .setContentIntent(openApp).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
      .addAction(0, "VERIFY", verify).addAction(0, "SNOOZE", snooze).setAutoCancel(true).build())
    } catch (_: SecurityException) {
      // Posting reminders must never roll back or retry an already-created transaction.
    }
  }
  fun cancel(occurrenceId: String) = NotificationManagerCompat.from(context).cancel(occurrenceId.hashCode())
  private fun format(minor: Long) = "%,.2f".format(minor / 100.0)
  companion object { const val CHANNEL = "recurring_transactions"; const val ACTION_VERIFY = "com.joeabouserhal.financetracker.action.VERIFY_RECURRING"; const val EXTRA_OCCURRENCE_ID = "occurrence_id"; private const val SUMMARY_ID = 7101 }
}

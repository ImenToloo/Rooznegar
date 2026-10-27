package com.rooznegar.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Uses setAlarmClock: fires on time even in Doze and needs no special exact-alarm permission.
 * Android shows a small alarm icon in the status bar while a reminder is pending.
 */
class AlarmScheduler(context: Context) {
    private val ctx = context.applicationContext
    private val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    private fun pending(id: String): PendingIntent {
        val i = Intent(ctx, ReminderReceiver::class.java)
            .setData(Uri.parse("rooznegar://task/$id")).putExtra("id", id)
        return PendingIntent.getBroadcast(
            ctx, id.hashCode(), i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancel(id: String) { am.cancel(pending(id)) }

    fun schedule(t: TaskEntity) {
        cancel(t.id)
        val at = t.reminderAt ?: return
        if (t.completed || t.archived || at <= System.currentTimeMillis()) return
        val show = PendingIntent.getActivity(
            ctx, 0, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(t.id))
    }
}

object Notifier {
    private const val CH = "reminders"

    fun show(c: Context, t: TaskEntity) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CH, "یادآورها", NotificationManager.IMPORTANCE_HIGH))
        val open = PendingIntent.getActivity(
            c, t.id.hashCode(),
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("یادآوری")
            .setContentText(t.title)
            .setStyle(Notification.BigTextStyle().bigText(t.title))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_REMINDER)
            .build()
        nm.notify(t.id.hashCode(), n)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val p = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val t = AppDb.get(c).dao().get(id)
                if (t != null && !t.completed && !t.archived) Notifier.show(c, t)
            } finally {
                p.finish()
            }
        }
    }
}

/** Alarms are cleared by a reboot or an app update, so they are re-registered from the database. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = goAsync()
        val s = AlarmScheduler(c)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDb.get(c).dao().upcomingReminders(System.currentTimeMillis()).forEach { s.schedule(it) }
            } finally {
                p.finish()
            }
        }
    }
}

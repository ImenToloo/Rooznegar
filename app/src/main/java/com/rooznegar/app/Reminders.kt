package com.rooznegar.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

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

    /** Returns null on success, or a short Persian message describing a problem (never throws). */
    fun schedule(t: TaskEntity): String? {
        return try {
            cancel(t.id)
            val at = t.reminderAt ?: return null
            if (t.completed || t.archived || at <= System.currentTimeMillis()) return null
            val show = PendingIntent.getActivity(
                ctx, 0, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            try {
                am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), pending(t.id))
                null
            } catch (e: SecurityException) {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(t.id))
                "اندروید زمان دقیق را اجازه نداد؛ یادآور ممکن است چند دقیقه دیرتر بیاید."
            }
        } catch (e: Throwable) {
            "ذخیره شد ولی تنظیم یادآور ممکن نشد (" + e.javaClass.simpleName + ": " + e.message + ")"
        }
    }
}

object Notifier {
    // A new channel id: Android never lets an app change sound/vibration of an existing channel.
    private const val CH = "alarm_v2"
    const val ACT_SNOOZE = "snooze"
    const val ACT_DONE = "done"

    private fun action(c: Context, t: TaskEntity, act: String, minutes: Int, label: String): Notification.Action {
        val i = Intent(c, ReminderActionReceiver::class.java)
            .setData(Uri.parse("rooznegar://act/$act$minutes/${t.id}"))
            .putExtra("id", t.id).putExtra("act", act).putExtra("min", minutes)
        val pi = PendingIntent.getBroadcast(
            c, (t.id + act + minutes).hashCode(), i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Action.Builder(
            Icon.createWithResource(c, android.R.drawable.ic_menu_recent_history), label, pi
        ).build()
    }

    fun show(c: Context, t: TaskEntity) {
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val ch = NotificationChannel(CH, "یادآورهای روزنگار (آلارم)", NotificationManager.IMPORTANCE_HIGH)
        ch.description = "هشدار صوتی و لرزشی برای کارهای دارای یادآور"
        ch.setSound(
            sound,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        )
        ch.enableVibration(true)
        ch.vibrationPattern = longArrayOf(0, 700, 350, 700, 350, 1000)
        ch.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        nm.createNotificationChannel(ch)
        nm.deleteNotificationChannel("reminders")

        val open = PendingIntent.getActivity(
            c, t.id.hashCode(),
            Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = Notification.Builder(c, CH)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
            .setContentTitle("⏰ یادآوری")
            .setContentText(t.title)
            .setStyle(Notification.BigTextStyle().bigText(t.title))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setTimeoutAfter(3 * 60 * 1000L)
            .addAction(action(c, t, ACT_SNOOZE, 10, "۱۰ دقیقه بعد"))
            .addAction(action(c, t, ACT_SNOOZE, 60, "۱ ساعت بعد"))
            .addAction(action(c, t, ACT_DONE, 0, "انجام شد"))
            .build()
        // Repeat sound and vibration until the user reacts (or 3 minutes pass), like an alarm.
        n.flags = n.flags or Notification.FLAG_INSISTENT
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

/** Handles the notification buttons: snooze (new reminder time) or mark the task done. */
class ReminderActionReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getStringExtra("id") ?: return
        val act = i.getStringExtra("act") ?: return
        val min = i.getIntExtra("min", 10)
        (c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(id.hashCode())
        val p = goAsync()
        val s = AlarmScheduler(c)
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = AppDb.get(c).dao()
                val t = dao.get(id)
                if (t != null) {
                    val u = if (act == Notifier.ACT_DONE) {
                        t.copy(completed = true, completedAt = System.currentTimeMillis())
                    } else {
                        t.copy(reminderAt = System.currentTimeMillis() + min * 60_000L)
                    }
                    dao.update(u)
                    s.schedule(u)
                }
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

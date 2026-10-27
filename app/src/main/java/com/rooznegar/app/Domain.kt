package com.rooznegar.app

import androidx.room.withTransaction
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

object Rollover {
    /** True when an unfinished task from a past day still needs the user's decision today. */
    fun needsDecision(
        scheduled: String, completed: Boolean, archived: Boolean, snoozedOn: String?, today: String
    ): Boolean = !completed && !archived && scheduled < today && snoozedOn != today
}

/** Keeps the clock time of a reminder but moves it to [newDate]; returns null if that is already past. */
fun shiftReminder(reminderAt: Long?, newDate: LocalDate, now: Long = System.currentTimeMillis()): Long? {
    if (reminderAt == null) return null
    val time = Instant.ofEpochMilli(reminderAt).atZone(TEHRAN).toLocalTime()
    val moved = newDate.atTime(time).atZone(TEHRAN).toInstant().toEpochMilli()
    return if (moved > now) moved else null
}

class TaskRepository(private val db: AppDb, private val scheduler: AlarmScheduler) {
    private val dao = db.dao()

    fun day(d: String) = dao.observeDay(d)
    fun overdue(today: String) = dao.observeOverdue(today)

    suspend fun add(title: String, date: String): Boolean {
        val t = title.trim().take(200)
        if (t.isEmpty()) return false
        val order = (dao.maxOrder(date) ?: 0L) + 1
        dao.insert(
            TaskEntity(
                id = UUID.randomUUID().toString(), title = t, createdAt = System.currentTimeMillis(),
                originalDate = date, scheduledDate = date, sortOrder = order
            )
        )
        return true
    }

    suspend fun setDone(id: String, done: Boolean) {
        val n = db.withTransaction {
            val t = dao.get(id) ?: return@withTransaction null
            val u = t.copy(completed = done, completedAt = if (done) System.currentTimeMillis() else null)
            dao.update(u)
            u
        }
        if (n != null) scheduler.schedule(n)
    }

    suspend fun delete(id: String) {
        db.withTransaction {
            dao.delete(id)
            dao.deleteHistory(id)
        }
        scheduler.cancel(id)
    }

    suspend fun snooze(id: String, today: String) = db.withTransaction {
        val t = dao.get(id) ?: return@withTransaction
        dao.update(t.copy(snoozedOn = today))
    }

    /** Edits title, priority, date and reminder in one transaction; a date change is logged in the history. */
    suspend fun update(id: String, title: String, priority: Boolean, date: String, reminderAt: Long?): Boolean {
        val clean = title.trim().take(200)
        if (clean.isEmpty()) return false
        val n = db.withTransaction {
            val t = dao.get(id) ?: return@withTransaction null
            var order = t.sortOrder
            val changed = date != t.scheduledDate
            if (changed) {
                order = (dao.maxOrder(date) ?: 0L) + 1
                dao.insertHistory(
                    RescheduleEntity(
                        taskId = id, fromDate = t.scheduledDate, toDate = date,
                        at = System.currentTimeMillis(), reason = "manual_edit", opId = UUID.randomUUID().toString()
                    )
                )
            }
            val u = t.copy(
                title = clean, priority = priority, scheduledDate = date, sortOrder = order,
                reminderAt = reminderAt, snoozedOn = if (changed) null else t.snoozedOn
            )
            dao.update(u)
            u
        } ?: return false
        scheduler.schedule(n)
        return true
    }

    /**
     * Idempotent: re-reads the task inside the transaction and only moves it when it is still
     * unfinished and still on [expectedFrom]. A repeated call finds a different date and does nothing.
     */
    suspend fun move(id: String, expectedFrom: String, to: String, reason: String): Boolean {
        val n = db.withTransaction {
            val t = dao.get(id) ?: return@withTransaction null
            if (t.completed || t.archived || t.scheduledDate != expectedFrom || expectedFrom == to) {
                return@withTransaction null
            }
            val order = (dao.maxOrder(to) ?: 0L) + 1
            val u = t.copy(
                scheduledDate = to, sortOrder = order, snoozedOn = null,
                reminderAt = shiftReminder(t.reminderAt, LocalDate.parse(to))
            )
            dao.update(u)
            dao.insertHistory(
                RescheduleEntity(
                    taskId = id, fromDate = expectedFrom, toDate = to,
                    at = System.currentTimeMillis(), reason = reason, opId = UUID.randomUUID().toString()
                )
            )
            u
        } ?: return false
        scheduler.schedule(n)
        return true
    }
}

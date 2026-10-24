package com.rooznegar.app

import androidx.room.withTransaction
import java.util.UUID

object Rollover {
    /** True when an unfinished task from a past day still needs the user's decision today. */
    fun needsDecision(
        scheduled: String, completed: Boolean, archived: Boolean, snoozedOn: String?, today: String
    ): Boolean = !completed && !archived && scheduled < today && snoozedOn != today
}

class TaskRepository(private val db: AppDb) {
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

    suspend fun setDone(id: String, done: Boolean) = db.withTransaction {
        val t = dao.get(id) ?: return@withTransaction
        dao.update(t.copy(completed = done, completedAt = if (done) System.currentTimeMillis() else null))
    }

    suspend fun delete(id: String) = db.withTransaction {
        dao.delete(id)
        dao.deleteHistory(id)
    }

    suspend fun snooze(id: String, today: String) = db.withTransaction {
        val t = dao.get(id) ?: return@withTransaction
        dao.update(t.copy(snoozedOn = today))
    }

    /**
     * Idempotent: re-reads the task inside the transaction and only moves it when it is still
     * unfinished and still on [expectedFrom]. A repeated call finds a different date and does nothing.
     */
    suspend fun move(id: String, expectedFrom: String, to: String, reason: String): Boolean =
        db.withTransaction {
            val t = dao.get(id) ?: return@withTransaction false
            if (t.completed || t.archived || t.scheduledDate != expectedFrom || expectedFrom == to) {
                return@withTransaction false
            }
            val order = (dao.maxOrder(to) ?: 0L) + 1
            dao.update(t.copy(scheduledDate = to, sortOrder = order, snoozedOn = null))
            dao.insertHistory(
                RescheduleEntity(
                    taskId = id, fromDate = expectedFrom, toDate = to,
                    at = System.currentTimeMillis(), reason = reason, opId = UUID.randomUUID().toString()
                )
            )
            true
        }
}

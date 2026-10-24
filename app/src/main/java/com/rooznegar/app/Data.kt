package com.rooznegar.app

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** Dates are stored as ISO strings (yyyy-MM-dd), a plain calendar day with no time zone. */
@Entity(tableName = "tasks", indices = [Index("scheduledDate")])
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val originalDate: String,
    val scheduledDate: String,
    val completed: Boolean = false,
    val completedAt: Long? = null,
    val sortOrder: Long = 0,
    val archived: Boolean = false,
    val snoozedOn: String? = null
)

@Entity(
    tableName = "reschedule_history",
    indices = [Index(value = ["opId"], unique = true), Index("taskId")]
)
data class RescheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: String,
    val fromDate: String,
    val toDate: String,
    val at: Long,
    val reason: String,
    val opId: String
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE scheduledDate = :d AND archived = 0 ORDER BY completed ASC, sortOrder ASC")
    fun observeDay(d: String): Flow<List<TaskEntity>>

    @Query(
        "SELECT * FROM tasks WHERE completed = 0 AND archived = 0 AND scheduledDate < :today " +
            "AND (snoozedOn IS NULL OR snoozedOn != :today) ORDER BY scheduledDate ASC, sortOrder ASC"
    )
    fun observeOverdue(today: String): Flow<List<TaskEntity>>

    @Insert suspend fun insert(t: TaskEntity)
    @Update suspend fun update(t: TaskEntity)
    @Query("SELECT * FROM tasks WHERE id = :id") suspend fun get(id: String): TaskEntity?
    @Query("SELECT MAX(sortOrder) FROM tasks WHERE scheduledDate = :d") suspend fun maxOrder(d: String): Long?
    @Query("DELETE FROM tasks WHERE id = :id") suspend fun delete(id: String)
    @Query("DELETE FROM reschedule_history WHERE taskId = :id") suspend fun deleteHistory(id: String)
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insertHistory(h: RescheduleEntity): Long
}

@Database(entities = [TaskEntity::class, RescheduleEntity::class], version = 1, exportSchema = false)
abstract class AppDb : RoomDatabase() {
    abstract fun dao(): TaskDao

    companion object {
        @Volatile private var instance: AppDb? = null
        fun get(c: Context): AppDb = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(c.applicationContext, AppDb::class.java, "rooznegar.db")
                .build().also { instance = it }
        }
    }
}

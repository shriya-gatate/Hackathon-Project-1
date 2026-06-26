package com.example.data.local

import androidx.room.*
import com.example.data.model.Task
import com.example.data.model.FocusSession
import com.example.data.model.CalendarEvent
import com.example.data.model.DailyScheduleItem
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductivityDao {

    // --- Tasks ---
    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, deadline ASC")
    fun getAllTasks(): Flow<List<Task>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: Int): Task?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task): Long

    @Update
    suspend fun updateTask(task: Task)

    @Delete
    suspend fun deleteTask(task: Task)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTaskById(id: Int)


    // --- Focus Sessions ---
    @Query("SELECT * FROM focus_sessions ORDER BY startTime DESC")
    fun getAllFocusSessions(): Flow<List<FocusSession>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFocusSession(session: FocusSession): Long

    @Delete
    suspend fun deleteFocusSession(session: FocusSession)


    // --- Calendar Events ---
    @Query("SELECT * FROM calendar_events ORDER BY startTime ASC")
    fun getAllCalendarEvents(): Flow<List<CalendarEvent>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCalendarEvent(event: CalendarEvent): Long

    @Delete
    suspend fun deleteCalendarEvent(event: CalendarEvent)

    @Query("DELETE FROM calendar_events")
    suspend fun clearCalendarEvents()


    // --- Daily Schedule ---
    @Query("SELECT * FROM daily_schedule_items ORDER BY id ASC")
    fun getDailySchedule(): Flow<List<DailyScheduleItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyScheduleItem(item: DailyScheduleItem): Long

    @Update
    suspend fun updateDailyScheduleItem(item: DailyScheduleItem)

    @Query("DELETE FROM daily_schedule_items")
    suspend fun clearDailySchedule()
}

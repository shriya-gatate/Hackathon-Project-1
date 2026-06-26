package com.example.data.repository

import com.example.data.local.ProductivityDao
import com.example.data.model.Task
import com.example.data.model.FocusSession
import com.example.data.model.CalendarEvent
import com.example.data.model.DailyScheduleItem
import kotlinx.coroutines.flow.Flow

class ProductivityRepository(private val dao: ProductivityDao) {

    val allTasks: Flow<List<Task>> = dao.getAllTasks()
    val allFocusSessions: Flow<List<FocusSession>> = dao.getAllFocusSessions()
    val allCalendarEvents: Flow<List<CalendarEvent>> = dao.getAllCalendarEvents()
    val dailySchedule: Flow<List<DailyScheduleItem>> = dao.getDailySchedule()

    suspend fun getTaskById(id: Int): Task? = dao.getTaskById(id)

    suspend fun insertTask(task: Task): Long = dao.insertTask(task)

    suspend fun updateTask(task: Task) = dao.updateTask(task)

    suspend fun deleteTask(task: Task) = dao.deleteTask(task)

    suspend fun deleteTaskById(id: Int) = dao.deleteTaskById(id)

    suspend fun insertFocusSession(session: FocusSession): Long = dao.insertFocusSession(session)

    suspend fun deleteFocusSession(session: FocusSession) = dao.deleteFocusSession(session)

    suspend fun insertCalendarEvent(event: CalendarEvent): Long = dao.insertCalendarEvent(event)

    suspend fun deleteCalendarEvent(event: CalendarEvent) = dao.deleteCalendarEvent(event)

    suspend fun clearCalendarEvents() = dao.clearCalendarEvents()

    suspend fun insertDailyScheduleItem(item: DailyScheduleItem): Long = dao.insertDailyScheduleItem(item)

    suspend fun updateDailyScheduleItem(item: DailyScheduleItem) = dao.updateDailyScheduleItem(item)

    suspend fun clearDailySchedule() = dao.clearDailySchedule()
}

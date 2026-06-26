package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.AppDatabase
import com.example.data.model.CalendarEvent
import com.example.data.model.DailyScheduleItem
import com.example.data.model.FocusSession
import com.example.data.model.Task
import com.example.data.repository.ProductivityRepository
import com.example.service.GeminiService
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.util.Calendar

class ProductivityViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "ProductivityViewModel"

    private val repository: ProductivityRepository
    
    // --- Room Flows ---
    val tasks: StateFlow<List<Task>>
    val focusSessions: StateFlow<List<FocusSession>>
    val calendarEvents: StateFlow<List<CalendarEvent>>
    val dailySchedule: StateFlow<List<DailyScheduleItem>>

    // --- Loading & UI States ---
    private val _isPredicting = MutableStateFlow(false)
    val isPredicting: StateFlow<Boolean> = _isPredicting.asStateFlow()

    private val _isGeneratingSchedule = MutableStateFlow(false)
    val isGeneratingSchedule: StateFlow<Boolean> = _isGeneratingSchedule.asStateFlow()

    private val _isDeconstructingTask = MutableStateFlow<Int?>(null) // task ID being deconstructed
    val isDeconstructingTask: StateFlow<Int?> = _isDeconstructingTask.asStateFlow()

    private val _smartReminders = MutableStateFlow<List<String>>(emptyList())
    val smartReminders: StateFlow<List<String>> = _smartReminders.asStateFlow()

    // --- Active Pomodoro / Focus Timer State ---
    private val _activeTaskId = MutableStateFlow<Int?>(null)
    val activeTaskId: StateFlow<Int?> = _activeTaskId.asStateFlow()

    private val _activeCategory = MutableStateFlow("Work")
    val activeCategory: StateFlow<String> = _activeCategory.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = ProductivityRepository(database.productivityDao())

        tasks = repository.allTasks.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        focusSessions = repository.allFocusSessions.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        calendarEvents = repository.allCalendarEvents.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        dailySchedule = repository.dailySchedule.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

        // Fetch reminders initially
        fetchSmartReminders()
        // Import mock calendar events if none exist to make UI immediately alive
        viewModelScope.launch {
            calendarEvents.first { true }.let { events ->
                if (events.isEmpty()) {
                    importMockCalendarEvents()
                }
            }
        }
    }

    // --- Tasks CRUD ---
    fun addTask(
        title: String,
        description: String,
        deadline: Long,
        priority: String,
        category: String,
        estimatedHours: Double
    ) {
        viewModelScope.launch {
            _isPredicting.value = true
            try {
                // Calculate hours remaining until deadline
                val msRemaining = deadline - System.currentTimeMillis()
                val hoursRemaining = if (msRemaining > 0) msRemaining.toDouble() / (1000 * 60 * 60) else 0.0

                // Get average recent focus rating
                val averageFocusRating = getAverageFocusRating()

                // Call Gemini Service to predict miss risk
                val prediction = GeminiService.predictMissedDeadline(
                    taskTitle = title,
                    taskDescription = description,
                    estimatedHours = estimatedHours,
                    hoursRemaining = hoursRemaining,
                    category = category,
                    recentFocusEfficiency = averageFocusRating
                )

                val newTask = Task(
                    title = title,
                    description = description,
                    deadline = deadline,
                    priority = priority,
                    category = category,
                    estimatedHours = estimatedHours,
                    missedDeadlineRisk = prediction.first,
                    aiPredictionReasoning = prediction.second
                )

                repository.insertTask(newTask)
                fetchSmartReminders() // Update reminders after adding task
            } catch (e: Exception) {
                Log.e(TAG, "Error adding task: ${e.message}", e)
            } finally {
                _isPredicting.value = false
            }
        }
    }

    fun updateTask(task: Task) {
        viewModelScope.launch {
            repository.updateTask(task)
        }
    }

    fun deleteTask(task: Task) {
        viewModelScope.launch {
            repository.deleteTask(task)
            fetchSmartReminders()
        }
    }

    fun toggleTaskCompletion(task: Task) {
        viewModelScope.launch {
            val updated = task.copy(
                isCompleted = !task.isCompleted,
                progress = if (!task.isCompleted) 100 else 0
            )
            repository.updateTask(updated)
            fetchSmartReminders()
        }
    }

    fun updateTaskProgress(task: Task, progress: Int) {
        viewModelScope.launch {
            val updated = task.copy(
                progress = progress,
                isCompleted = progress >= 100
            )
            repository.updateTask(updated)
        }
    }

    /**
     * Force re-evaluate prediction for a specific task.
     */
    fun reEvaluatePrediction(task: Task) {
        viewModelScope.launch {
            _isPredicting.value = true
            try {
                val msRemaining = task.deadline - System.currentTimeMillis()
                val hoursRemaining = if (msRemaining > 0) msRemaining.toDouble() / (1000 * 60 * 60) else 0.0
                val averageFocusRating = getAverageFocusRating()

                val prediction = GeminiService.predictMissedDeadline(
                    taskTitle = task.title,
                    taskDescription = task.description,
                    estimatedHours = task.estimatedHours,
                    hoursRemaining = hoursRemaining,
                    category = task.category,
                    recentFocusEfficiency = averageFocusRating
                )

                val updatedTask = task.copy(
                    missedDeadlineRisk = prediction.first,
                    aiPredictionReasoning = prediction.second
                )
                repository.updateTask(updatedTask)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating task prediction: ${e.message}", e)
            } finally {
                _isPredicting.value = false
            }
        }
    }

    /**
     * Generates a step-by-step deconstruction for a specific task.
     */
    fun deconstructTask(task: Task) {
        viewModelScope.launch {
            _isDeconstructingTask.value = task.id
            try {
                val stepsList = GeminiService.generateStepByStepGuide(task.title, task.description)
                
                // Convert list to JSON Array to save in Room DB
                val jsonArray = JSONArray()
                stepsList.forEach { jsonArray.put(it) }

                val updatedTask = task.copy(
                    aiStepsJson = jsonArray.toString()
                )
                repository.updateTask(updatedTask)
            } catch (e: Exception) {
                Log.e(TAG, "Error deconstructing task: ${e.message}", e)
            } finally {
                _isDeconstructingTask.value = null
            }
        }
    }

    // --- Daily Schedule operations ---
    fun generateDailySchedule(targetFocusHours: Int) {
        viewModelScope.launch {
            _isGeneratingSchedule.value = true
            try {
                val activeTasksList = tasks.value.filter { !it.isCompleted }.map { "${it.title} (${it.category})" }
                val calendarEventsList = calendarEvents.value.map { "${it.title} (${formatTimeRange(it.startTime, it.endTime)})" }

                val scheduleTriples = GeminiService.generateDailySchedule(
                    activeTasks = activeTasksList,
                    calendarEvents = calendarEventsList,
                    targetFocusHours = targetFocusHours
                )

                repository.clearDailySchedule()

                scheduleTriples.forEach { triple ->
                    repository.insertDailyScheduleItem(
                        DailyScheduleItem(
                            timeLabel = triple.first,
                            title = triple.second,
                            description = triple.third,
                            isCompleted = false
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error generating daily schedule: ${e.message}", e)
            } finally {
                _isGeneratingSchedule.value = false
            }
        }
    }

    fun toggleScheduleItemCompletion(item: DailyScheduleItem) {
        viewModelScope.launch {
            repository.updateDailyScheduleItem(item.copy(isCompleted = !item.isCompleted))
        }
    }

    fun clearSchedule() {
        viewModelScope.launch {
            repository.clearDailySchedule()
        }
    }

    // --- Focus Sessions (Pomodoro) ---
    fun selectTaskForFocus(taskId: Int?, category: String) {
        _activeTaskId.value = taskId
        _activeCategory.value = category
    }

    fun logFocusSession(durationMinutes: Int, rating: Int, notes: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val start = now - (durationMinutes * 60 * 1000)
            
            val session = FocusSession(
                taskId = _activeTaskId.value,
                category = _activeCategory.value,
                startTime = start,
                endTime = now,
                durationMinutes = durationMinutes,
                focusRating = rating,
                notes = notes
            )
            repository.insertFocusSession(session)

            // Update focus percentage / progress for active task if selected
            _activeTaskId.value?.let { activeId ->
                tasks.value.find { it.id == activeId }?.let { activeTask ->
                    val totalDurationForTask = focusSessions.value
                        .filter { it.taskId == activeId }
                        .sumOf { it.durationMinutes } + durationMinutes
                    
                    val estimatedMinutes = activeTask.estimatedHours * 60
                    val progressPercentage = if (estimatedMinutes > 0) {
                        ((totalDurationForTask.toDouble() / estimatedMinutes) * 100).toInt().coerceAtMost(99)
                    } else {
                        50
                    }
                    updateTaskProgress(activeTask, progressPercentage)
                }
            }

            // Refresh smart reminders to account for the new focus session
            fetchSmartReminders()
        }
    }

    // --- Calendar Operations ---
    fun addCalendarEvent(title: String, description: String, startTime: Long, endTime: Long) {
        viewModelScope.launch {
            val event = CalendarEvent(
                title = title,
                description = description,
                startTime = startTime,
                endTime = endTime,
                isSynced = true,
                source = "Personal Calendar"
            )
            repository.insertCalendarEvent(event)
        }
    }

    fun deleteCalendarEvent(event: CalendarEvent) {
        viewModelScope.launch {
            repository.deleteCalendarEvent(event)
        }
    }

    fun importMockCalendarEvents() {
        viewModelScope.launch {
            repository.clearCalendarEvents()
            
            val cal = Calendar.getInstance()
            
            // Event 1: Today morning sync
            cal.set(Calendar.HOUR_OF_DAY, 10)
            cal.set(Calendar.MINUTE, 0)
            val event1Start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 10)
            cal.set(Calendar.MINUTE, 45)
            val event1End = cal.timeInMillis

            repository.insertCalendarEvent(
                CalendarEvent(
                    title = "⚡ Weekly Project Sync",
                    description = "Alignment call with the design and engineering team regarding deliverables.",
                    startTime = event1Start,
                    endTime = event1End,
                    isSynced = true,
                    source = "Google Calendar"
                )
            )

            // Event 2: Today afternoon workshop
            cal.set(Calendar.HOUR_OF_DAY, 14)
            cal.set(Calendar.MINUTE, 0)
            val event2Start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 15)
            cal.set(Calendar.MINUTE, 30)
            val event2End = cal.timeInMillis

            repository.insertCalendarEvent(
                CalendarEvent(
                    title = "🎨 Design System Review",
                    description = "Discuss style variables, spacing tables, and interactive ripples guidelines.",
                    startTime = event2Start,
                    endTime = event2End,
                    isSynced = true,
                    source = "Google Calendar"
                )
            )

            // Event 3: Tomorrow morning check-in
            cal.add(Calendar.DAY_OF_YEAR, 1)
            cal.set(Calendar.HOUR_OF_DAY, 9)
            cal.set(Calendar.MINUTE, 30)
            val event3Start = cal.timeInMillis
            cal.set(Calendar.HOUR_OF_DAY, 10)
            cal.set(Calendar.MINUTE, 0)
            val event3End = cal.timeInMillis

            repository.insertCalendarEvent(
                CalendarEvent(
                    title = "📈 Focus Coaching Call",
                    description = "One-on-one reviews of productivity schedules and missed deadline predictions.",
                    startTime = event3Start,
                    endTime = event3End,
                    isSynced = true,
                    source = "Microsoft Outlook"
                )
            )
        }
    }

    // --- Smart Reminders ---
    fun fetchSmartReminders() {
        viewModelScope.launch {
            val pendingTasksCount = tasks.value.count { !it.isCompleted }
            val completedTodayCount = tasks.value.count { it.isCompleted } // Simulating completion count
            val recentMinutes = focusSessions.value.sumOf { it.durationMinutes }

            val reminders = GeminiService.generateSmartReminders(
                userName = "Scholar",
                pendingTasksCount = pendingTasksCount,
                completedTodayCount = completedTodayCount,
                recentFocusMinutes = recentMinutes
            )
            _smartReminders.value = reminders
        }
    }

    // --- Helpers ---
    private fun getAverageFocusRating(): Double {
        val sessions = focusSessions.value
        if (sessions.isEmpty()) return 3.5 // baseline focus efficiency
        return sessions.map { it.focusRating }.average()
    }

    private fun formatTimeRange(start: Long, end: Long): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = start
        val startHour = cal.get(Calendar.HOUR_OF_DAY)
        val startMin = cal.get(Calendar.MINUTE)
        cal.timeInMillis = end
        val endHour = cal.get(Calendar.HOUR_OF_DAY)
        val endMin = cal.get(Calendar.MINUTE)

        return String.format("%02d:%02d - %02d:%02d", startHour, startMin, endHour, endMin)
    }
}

package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val description: String,
    val deadline: Long, // timestamp
    val priority: String, // "HIGH", "MEDIUM", "LOW"
    val category: String, // "Work", "Study", "Personal", "Other"
    val estimatedHours: Double,
    val progress: Int = 0, // 0 to 100
    val isCompleted: Boolean = false,
    val missedDeadlineRisk: Double = 0.0, // 0.0 to 1.0 (0% to 100%)
    val aiPredictionReasoning: String = "",
    val calendarEventId: String? = null,
    val aiStepsJson: String = "" // JSON-encoded list of step-by-step instructions
)

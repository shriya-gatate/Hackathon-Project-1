package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_schedule_items")
data class DailyScheduleItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val timeLabel: String, // e.g. "09:00 AM - 10:00 AM"
    val title: String,
    val description: String,
    val isCompleted: Boolean = false,
    val taskId: Int? = null
)

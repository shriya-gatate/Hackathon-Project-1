package com.example.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions")
data class FocusSession(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val taskId: Int? = null,
    val category: String,
    val startTime: Long,
    val endTime: Long,
    val durationMinutes: Int,
    val focusRating: Int, // 1 to 5
    val notes: String = ""
)

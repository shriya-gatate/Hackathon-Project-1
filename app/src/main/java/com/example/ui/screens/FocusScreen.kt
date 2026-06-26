package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.data.model.FocusSession
import com.example.data.model.Task
import com.example.ui.components.CategoryDistributionChart
import com.example.ui.components.FocusWeeklyBarChart
import com.example.viewmodel.ProductivityViewModel
import kotlinx.coroutines.delay
import java.util.Calendar

@Composable
fun FocusScreen(
    viewModel: ProductivityViewModel,
    modifier: Modifier = Modifier
) {
    val tasks by viewModel.tasks.collectAsState()
    val sessions by viewModel.focusSessions.collectAsState()
    val activeTaskId by viewModel.activeTaskId.collectAsState()
    val activeCategory by viewModel.activeCategory.collectAsState()

    val activeTask = remember(tasks, activeTaskId) {
        tasks.find { it.id == activeTaskId }
    }

    // Timer States
    val initialTimeSec = 25 * 60 // 25 Minutes standard Pomodoro
    var timeLeftSec by remember { mutableStateOf(initialTimeSec) }
    var isTimerRunning by remember { mutableStateOf(false) }
    var showRatingDialog by remember { mutableStateOf(false) }

    // Dropdown States
    var showTaskDropdown by remember { mutableStateOf(false) }

    // Start background ticking coroutine
    LaunchedEffect(isTimerRunning) {
        if (isTimerRunning) {
            while (timeLeftSec > 0) {
                delay(1000)
                timeLeftSec--
            }
            // Timer Finished!
            isTimerRunning = false
            showRatingDialog = true
        }
    }

    // Analytics Derived States
    val categoryStats = remember(sessions) {
        val stats = mutableMapOf<String, Int>()
        sessions.forEach {
            stats[it.category] = (stats[it.category] ?: 0) + it.durationMinutes
        }
        stats
    }

    val weeklyStats = remember(sessions) {
        val dailyMinutes = MutableList(7) { 0 }
        val cal = Calendar.getInstance()
        sessions.forEach {
            cal.timeInMillis = it.startTime
            // Day of week: 1 (Sunday) to 7 (Saturday). Map to index 0 (Mon) to 6 (Sun).
            val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
            val index = when (dayOfWeek) {
                Calendar.MONDAY -> 0
                Calendar.TUESDAY -> 1
                Calendar.WEDNESDAY -> 2
                Calendar.THURSDAY -> 3
                Calendar.FRIDAY -> 4
                Calendar.SATURDAY -> 5
                Calendar.SUNDAY -> 6
                else -> 0
            }
            dailyMinutes[index] += it.durationMinutes
        }
        dailyMinutes
    }

    val totalLoggedMinutes = remember(sessions) {
        sessions.sumOf { it.durationMinutes }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Top Header
        item {
            Column {
                Text(
                    text = "Focus Lounge",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Enter deep focus state and log focus stats",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Timer Panel Card
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Task Selection Row
                    Box {
                        Surface(
                            shape = RoundedCornerShape(24.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .clickable { showTaskDropdown = true }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Assignment,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = activeTask?.title ?: "Select Companion Task...",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showTaskDropdown,
                            onDismissRequest = { showTaskDropdown = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("None (Generic Focus)") },
                                onClick = {
                                    viewModel.selectTaskForFocus(null, "Work")
                                    showTaskDropdown = false
                                }
                            )
                            tasks.filter { !it.isCompleted }.forEach { task ->
                                DropdownMenuItem(
                                    text = { Text("${task.title} (${task.category})") },
                                    onClick = {
                                        viewModel.selectTaskForFocus(task.id, task.category)
                                        showTaskDropdown = false
                                    }
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Circular Arc Timer Display
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val progress = timeLeftSec.toFloat() / initialTimeSec
                        val strokeColor = MaterialTheme.colorScheme.primary

                        Canvas(modifier = Modifier.fillMaxSize()) {
                            // Base track
                            drawCircle(
                                color = strokeColor.copy(alpha = 0.1f),
                                style = Stroke(width = 8.dp.toPx())
                            )
                            // Progress arc
                            drawArc(
                                color = strokeColor,
                                startAngle = -90f,
                                sweepAngle = progress * 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val mins = timeLeftSec / 60
                            val secs = timeLeftSec % 60
                            Text(
                                text = String.format("%02d:%02d", mins, secs),
                                style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (isTimerRunning) "STAY FOCUSED" else "READY TO FOCUS",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // Timer Control Row
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = {
                                isTimerRunning = false
                                timeLeftSec = initialTimeSec
                            },
                            modifier = Modifier
                                .testTag("reset_timer_button")
                                .minimumInteractiveComponentSize()
                        ) {
                            Text("Reset")
                        }

                        Button(
                            onClick = { isTimerRunning = !isTimerRunning },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isTimerRunning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .testTag("toggle_timer_button")
                                .minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (isTimerRunning) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isTimerRunning) "Pause" else "Start"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (isTimerRunning) "Pause" else "Focus 25m")
                        }

                        // Debug finish button to easily mock focus sessions without waiting 25 minutes!
                        IconButton(
                            onClick = {
                                isTimerRunning = false
                                showRatingDialog = true
                            },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = Icons.Default.SkipNext,
                                contentDescription = "Mock Complete Session",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        // Stats Header Section
        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Analytics, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Focus Analytics & Patterns",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Metric Cards Section
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Total Logged", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("$totalLoggedMinutes mins", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        Text("All focus intervals", style = MaterialTheme.typography.labelSmall, color = Color(0xFF4CAF50))
                    }
                }

                Card(
                    modifier = Modifier.weight(1f),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Active Sessions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${sessions.size}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
                        Text("Streak: ${sessions.size} sessions", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        // Charts Section
        if (sessions.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Focus Distribution by Category",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                        CategoryDistributionChart(
                            categoryData = categoryStats,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    FocusWeeklyBarChart(
                        weeklySessions = weeklyStats,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }
            }
        } else {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.outline)
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("No focus patterns logged yet.", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                            Text("Your weekly & category analytics will appear here after your first completed focus session.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    // Rating & Logging Dialog
    if (showRatingDialog) {
        FocusRatingDialog(
            taskTitle = activeTask?.title ?: "Generic Focus Session",
            onDismiss = { showRatingDialog = false },
            onSave = { rating, notes ->
                viewModel.logFocusSession(25, rating, notes)
                timeLeftSec = initialTimeSec
                showRatingDialog = false
            }
        )
    }
}

@Composable
fun FocusRatingDialog(
    taskTitle: String,
    onDismiss: () -> Unit,
    onSave: (Int, String) -> Unit
) {
    var rating by remember { mutableStateOf(4) }
    var notes by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Session Complete!",
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                Text(
                    text = "Congratulations! You completed a 25-minute focus session for '$taskTitle'. Log your cognitive metrics below.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                // Star Rating Picker
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    (1..5).forEach { star ->
                        IconButton(
                            onClick = { rating = star },
                            modifier = Modifier.minimumInteractiveComponentSize()
                        ) {
                            Icon(
                                imageVector = if (star <= rating) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "$star Stars",
                                tint = if (star <= rating) Color(0xFFFFC107) else MaterialTheme.colorScheme.outline,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }
                }

                // Star Rating Description text
                val ratingDesc = when (rating) {
                    1 -> "Severe distraction / procrastination"
                    2 -> "Low focus, frequently drifted"
                    3 -> "Moderate focus with some interruptions"
                    4 -> "High focus and solid flow state"
                    else -> "Absolute hyperfocus block"
                }
                Text(
                    text = ratingDesc,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("What did you work on? (Notes)") },
                    minLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, modifier = Modifier.minimumInteractiveComponentSize()) {
                        Text("Cancel")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { onSave(rating, notes) },
                        modifier = Modifier.testTag("save_session_button")
                    ) {
                        Text("Save & Update Progress")
                    }
                }
            }
        }
    }
}

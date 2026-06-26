package com.example.service

import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object GeminiService {
    private const val TAG = "GeminiService"
    private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Checks if the API key is configured.
     */
    fun isApiKeyConfigured(): Boolean {
        val key = BuildConfig.GEMINI_API_KEY
        return key.isNotEmpty() && key != "MY_GEMINI_API_KEY"
    }

    private suspend fun callGeminiApi(prompt: String, jsonMode: Boolean = false): String? = withContext(Dispatchers.IO) {
        if (!isApiKeyConfigured()) {
            Log.w(TAG, "API Key is not configured. Returning null.")
            return@withContext null
        }

        try {
            val url = "$BASE_URL?key=${BuildConfig.GEMINI_API_KEY}"

            // Build request JSON
            val requestJson = JSONObject().apply {
                val contentsArray = JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", prompt)
                            })
                        })
                    })
                }
                put("contents", contentsArray)

                if (jsonMode) {
                    put("generationConfig", JSONObject().apply {
                        put("responseMimeType", "application/json")
                        put("temperature", 0.2)
                    })
                } else {
                    put("generationConfig", JSONObject().apply {
                        put("temperature", 0.7)
                    })
                }
            }

            val requestBody = requestJson.toString().toRequestBody("application/json".toMediaType())

            val request = Request.Builder()
                .url(url)
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "API call failed with code: ${response.code}, body: ${response.body?.string()}")
                    return@withContext null
                }

                val responseBody = response.body?.string() ?: return@withContext null
                val jsonResponse = JSONObject(responseBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val candidate = candidates.getJSONObject(0)
                    val content = candidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text")
                    }
                }
                return@withContext null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error calling Gemini API: ${e.message}", e)
            return@withContext null
        }
    }

    /**
     * Predicts missed deadlines based on task properties.
     * Returns a Pair of (Risk Score from 0.0 to 1.0, Reasoning String)
     */
    suspend fun predictMissedDeadline(
        taskTitle: String,
        taskDescription: String,
        estimatedHours: Double,
        hoursRemaining: Double,
        category: String,
        recentFocusEfficiency: Double // e.g. average focus rating (1 to 5) converted to a scale
    ): Pair<Double, String> {
        val prompt = """
            You are an expert project manager and cognitive performance model. Analyze the risk of missing a deadline for this task.
            
            Task Details:
            - Title: $taskTitle
            - Description: $taskDescription
            - Estimated work hours required: $estimatedHours
            - Hours remaining until deadline: $hoursRemaining hours
            - Category: $category
            - User's recent focus rating (1-5, where 5 is high focus): $recentFocusEfficiency
            
            Evaluate if this task will be finished in time, considering standard human procrastination, task complexity, overhead, and focus efficiency.
            Return a JSON object containing EXACTLY:
            - "riskScore": A float value between 0.0 (no risk) and 1.0 (certain to miss deadline).
            - "reasoning": A 2-3 sentence friendly but objective assessment of WHY this risk score was given, and a constructive tip to avoid it.
        """.trimIndent()

        val jsonResponse = callGeminiApi(prompt, jsonMode = true) ?: return fallbackPrediction(estimatedHours, hoursRemaining)

        return try {
            val json = JSONObject(jsonResponse)
            val score = json.optDouble("riskScore", 0.0)
            val reasoning = json.optString("reasoning", "Unable to generate custom analysis.")
            Pair(score, reasoning)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing prediction response", e)
            fallbackPrediction(estimatedHours, hoursRemaining)
        }
    }

    /**
     * Generates a step-by-step deconstruction guide for a task.
     * Returns a List of subtasks/steps.
     */
    suspend fun generateStepByStepGuide(taskTitle: String, taskDescription: String): List<String> {
        val prompt = """
            Deconstruct this task into a sequential list of 4 to 6 highly actionable, bite-sized steps (15 to 45 minutes each).
            
            Task: $taskTitle
            Description: $taskDescription
            
            Format the response as a JSON array of strings. Each string must be a clear, self-contained action statement starting with a verb.
            Example format: ["Understand criteria and write draft structure", "Research source materials for section 1"]
        """.trimIndent()

        val jsonResponse = callGeminiApi(prompt, jsonMode = true) ?: return fallbackSteps(taskTitle)

        return try {
            val jsonArray = JSONArray(jsonResponse)
            val steps = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                steps.add(jsonArray.getString(i))
            }
            steps
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing step deconstruction response", e)
            fallbackSteps(taskTitle)
        }
    }

    /**
     * Generates a time-blocked daily schedule from active tasks.
     * Returns list of Map containing time label, title, and description.
     */
    suspend fun generateDailySchedule(
        activeTasks: List<String>,
        calendarEvents: List<String>,
        targetFocusHours: Int
    ): List<Triple<String, String, String>> {
        val tasksString = activeTasks.joinToString("\n- ")
        val eventsString = calendarEvents.joinToString("\n- ")

        val prompt = """
            Generate a personalized, time-blocked daily schedule. The goal is to allocate $targetFocusHours hours of focused deep-work time.
            
            Tasks to fit in:
            - $tasksString
            
            Pre-existing Calendar Commitments:
            - $eventsString
            
            Design a healthy, realistic 8 AM to 6 PM day including morning setup, focus blocks (maximum 90 mins each), breaks/lunch, and an afternoon review.
            Ensure you integrate the Pre-existing Calendar Commitments seamlessly.
            
            Return a JSON array of objects, where each object has:
            - "timeLabel": e.g., "09:00 AM - 10:30 AM"
            - "title": e.g., "Deep Work: Design Review" or "Lunch Break" or "Team Sync"
            - "description": A short sentence on how to approach this block or what to focus on.
        """.trimIndent()

        val jsonResponse = callGeminiApi(prompt, jsonMode = true) ?: return fallbackSchedule()

        return try {
            val jsonArray = JSONArray(jsonResponse)
            val schedule = mutableListOf<Triple<String, String, String>>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                schedule.add(Triple(
                    obj.optString("timeLabel", "00:00 - 00:00"),
                    obj.optString("title", "Block"),
                    obj.optString("description", "")
                ))
            }
            schedule
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing daily schedule", e)
            fallbackSchedule()
        }
    }

    /**
     * Generates 3 personalized smart reminders or notifications.
     */
    suspend fun generateSmartReminders(
        userName: String,
        pendingTasksCount: Int,
        completedTodayCount: Int,
        recentFocusMinutes: Int
    ): List<String> {
        val prompt = """
            Generate 3 short, highly personalized, friendly, and motivational "Smart Reminders" or cognitive coaching tips for the user.
            
            User context:
            - Name: $userName
            - Pending tasks: $pendingTasksCount
            - Tasks completed today: $completedTodayCount
            - Total focus minutes recorded recently: $recentFocusMinutes mins
            
            Make them diverse: one checking in on energy, one micro-action prompt, and one celebrating small wins or tracking focus patterns.
            Return a JSON array of 3 strings. Each string should be under 120 characters, energetic, and highly actionable.
        """.trimIndent()

        val jsonResponse = callGeminiApi(prompt, jsonMode = true) ?: return fallbackReminders()

        return try {
            val jsonArray = JSONArray(jsonResponse)
            val reminders = mutableListOf<String>()
            for (i in 0 until jsonArray.length()) {
                reminders.add(jsonArray.getString(i))
            }
            reminders
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing smart reminders", e)
            fallbackReminders()
        }
    }

    // --- Fallbacks for offline / missing API key mode ---

    private fun fallbackPrediction(estimatedHours: Double, hoursRemaining: Double): Pair<Double, String> {
        val ratio = if (hoursRemaining > 0) estimatedHours / hoursRemaining else 2.0
        val score = when {
            ratio >= 1.5 -> 0.9
            ratio >= 1.0 -> 0.7
            ratio >= 0.6 -> 0.45
            ratio >= 0.3 -> 0.2
            else -> 0.05
        }
        val reasoning = when {
            score >= 0.7 -> "High Risk! You have $estimatedHours hours of estimated work but only $hoursRemaining hours before the deadline. We highly recommend starting immediately and deconstructing this task."
            score >= 0.4 -> "Moderate Risk. The timeline is tight. Try setting a focused Pomodoro session to gain early momentum."
            else -> "Low Risk. You have plenty of time. Keep up the steady pace to finish comfortably!"
        }
        return Pair(score, reasoning)
    }

    private fun fallbackSteps(taskTitle: String): List<String> {
        return listOf(
            "Phase 1: Define scope and gather required references for '$taskTitle'",
            "Phase 2: Draft the initial structural outline and set milestones",
            "Phase 3: Execute the core technical or creative work (Deep Work block)",
            "Phase 4: Review, refine details, and perform quality checks",
            "Phase 5: Final touch-ups and submit before the deadline"
        )
    }

    private fun fallbackSchedule(): List<Triple<String, String, String>> {
        return listOf(
            Triple("08:30 AM - 09:00 AM", "Morning Kickstart", "Review your priorites, check calendar sync, and grab some coffee."),
            Triple("09:00 AM - 10:30 AM", "Deep Focus Block 1", "Tackle your highest priority task with absolute concentration."),
            Triple("10:30 AM - 11:00 AM", "Restorative Break", "Step away from the screen, stretch, and hydrate."),
            Triple("11:00 AM - 12:30 PM", "Deep Focus Block 2", "Continue important work or resolve complex blockers."),
            Triple("12:30 PM - 01:30 PM", "Lunch & Unwind", "Disconnect completely from work to recharge your cognitive battery."),
            Triple("01:30 PM - 03:00 PM", "Collaborative & Admin", "Respond to messages, attend sync calls, and sort quick tasks."),
            Triple("03:00 PM - 03:20 PM", "Quick Walk Break", "Circulate energy to beat the mid-afternoon energy dip."),
            Triple("03:20 PM - 05:00 PM", "Creative Execution", "Wrap up leftover tasks or work on a low-stress personal project."),
            Triple("05:00 PM - 05:30 PM", "Reflection & Sync", "Log your focus sessions, update status, and plan tomorrow.")
        )
    }

    private fun fallbackReminders(): List<String> {
        return listOf(
            "💡 Pro tip: Break your largest task into 3 tiny sub-steps. Momentum beats motivation!",
            "⚡ Focus Check: Clear physical desk space of clutter. A clean workspace clears your mind.",
            "🌟 High Five: You've got this! A 25-minute Pomodoro block is all it takes to build inertia."
        )
    }
}

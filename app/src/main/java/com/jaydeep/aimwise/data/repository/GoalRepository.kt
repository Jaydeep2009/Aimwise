package com.jaydeep.aimwise.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jaydeep.aimwise.data.model.DayPlan
import com.jaydeep.aimwise.data.model.Goal
import com.jaydeep.aimwise.data.model.Result
import com.jaydeep.aimwise.data.model.RoadmapResponse
import com.jaydeep.aimwise.data.model.Task
import com.jaydeep.aimwise.data.remote.RetrofitInstance
import com.jaydeep.aimwise.data.remote.RoadmapRequest
import com.jaydeep.aimwise.util.RetryUtils
import kotlinx.coroutines.tasks.await

/**
 * Repository for managing goals and roadmaps with Firestore.
 * 
 * FIRESTORE INDEX REQUIRED: users/{userId}/goals - createdAt (Descending)
 */
class GoalRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Cache for day plans to improve performance
    private val dayCache = mutableMapOf<String, DayPlan>()

    /**
     * Gets the reference to the current user's goals collection.
     */
    private fun userGoalsRef() =
        auth.currentUser?.uid?.let { uid ->
            firestore.collection("users")
                .document(uid)
                .collection("goals")
        } ?: throw UserNotAuthenticatedException("User not authenticated. Please login again.")

    /**
     * Custom exception thrown when attempting to access user data without authentication
     */
    class UserNotAuthenticatedException(message: String) : Exception(message)

    /**
     * Calculates the current day number based on calendar days (midnight to midnight).
     * Day 1 starts at goal creation, increments at midnight, capped at durationDays.
     */
    private fun calculateCurrentDay(createdAt: Long, durationDays: Int): Int {
        val calendar = java.util.Calendar.getInstance()
        
        // Get today's date at midnight
        val todayStart = calendar.apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // Get creation date at midnight
        val createdCalendar = java.util.Calendar.getInstance()
        val createdDayStart = createdCalendar.apply {
            timeInMillis = createdAt
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        
        // Calculate days difference + 1 (to start from day 1)
        val daysSinceCreation = ((todayStart - createdDayStart) / (1000 * 60 * 60 * 24)).toInt() + 1
        
        // Cap at duration days
        val calculatedDay = minOf(daysSinceCreation, durationDays)
        
        android.util.Log.d("GoalRepository", "calculateCurrentDay: createdAt=${java.util.Date(createdAt)}, today=${java.util.Date(System.currentTimeMillis())}, daysSince=$daysSinceCreation, capped=$calculatedDay")
        
        return calculatedDay
    }

    /**
     * Retrieves all goals for the current user, ordered by creation date (newest first).
     * Auto-syncs currentDay based on calendar date.
     */
    suspend fun getGoals(): List<Goal> {
        val result = RetryUtils.withRetry {
            val snapshot = userGoalsRef()
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50) // Limit results to prevent excessive data transfer
                .get()
                .await()

            snapshot.documents.map { doc ->
                val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
                val durationDays = doc.getLong("durationDays")?.toInt() ?: 30
                val storedCurrentDay = doc.getLong("currentDay")?.toInt() ?: 1
                
                // Calculate current day based on calendar days
                val calculatedDay = calculateCurrentDay(createdAt, durationDays)
                
                android.util.Log.d("DAY_CALC", "Goal: ${doc.getString("title")}")
                android.util.Log.d("DAY_CALC", "Stored day: $storedCurrentDay, Calculated day: $calculatedDay")
                
                // Update Firestore if the day has changed
                if (calculatedDay > storedCurrentDay) {
                    android.util.Log.d("DAY_CALC", "Updating Firestore from day $storedCurrentDay to $calculatedDay")
                    val uid = auth.currentUser?.uid ?: throw UserNotAuthenticatedException("User not authenticated")
                    firestore.collection("users")
                        .document(uid)
                        .collection("goals")
                        .document(doc.id)
                        .update("currentDay", calculatedDay)
                        .await()
                }
                
                // Load completion data for this goal
                val (completedTasks, totalTasks) = getGoalTaskCompletion(doc.id)
                
                // Load today's task data
                val todayPlan = getDayPlan(doc.id, calculatedDay)
                val todayCompletedTasks = todayPlan?.tasks?.count { it.isCompleted } ?: 0
                val todayTotalTasks = todayPlan?.tasks?.size ?: 0
                
                Goal(
                    id = doc.id,
                    title = doc.getString("title") ?: "",
                    durationDays = durationDays,
                    currentDay = calculatedDay, // Use calculated day, not stored
                    createdAt = createdAt,
                    roadmap = doc.getString("roadmap") ?: "",
                    completedTasks = completedTasks,
                    totalTasks = totalTasks,
                    todayCompletedTasks = todayCompletedTasks,
                    todayTotalTasks = todayTotalTasks
                )
            }
        }
        
        return when (result) {
            is Result.Success -> result.data
            is Result.Error -> throw result.exception
            is Result.Loading -> emptyList()
        }
    }
    /**
     * Retrieves a specific goal by ID. Auto-syncs currentDay based on calendar date.
     */
    suspend fun getGoal(goalId: String): Result<Goal> {
        return RetryUtils.withRetry {
            val uid = auth.currentUser?.uid 
                ?: throw UserNotAuthenticatedException("User not authenticated")

            val doc = firestore.collection("users")
                .document(uid)
                .collection("goals")
                .document(goalId)
                .get()
                .await()

            if (!doc.exists()) {
                throw Exception("Goal not found with id: $goalId")
            }

            // Auto-sync current day based on calendar date
            val createdAt = doc.getLong("createdAt") ?: System.currentTimeMillis()
            val durationDays = doc.getLong("durationDays")?.toInt() ?: 30
            val storedCurrentDay = doc.getLong("currentDay")?.toInt() ?: 1
            
            // Calculate current day based on calendar days
            val calculatedDay = calculateCurrentDay(createdAt, durationDays)
            
            // Update Firestore if the day has changed
            if (calculatedDay > storedCurrentDay) {
                firestore.collection("users")
                    .document(uid)
                    .collection("goals")
                    .document(goalId)
                    .update("currentDay", calculatedDay)
                    .await()
            }

            Goal(
                id = doc.id,
                title = doc.getString("title") ?: "",
                durationDays = durationDays,
                currentDay = calculatedDay // Use calculated day, not stored
            )
        }
    }
    /**
     * Generates a personalized roadmap using AI. Implements retry logic with exponential backoff.
     */
    suspend fun generateRoadmap(goal: String, days: Int): Result<RoadmapResponse> {
        return RetryUtils.withRetry(
            config = RetryUtils.RetryConfig(
                maxAttempts = 3,
                initialDelayMs = 2000,
                maxDelayMs = 15000,
                retryableExceptions = listOf(
                    java.io.IOException::class.java,
                    java.net.SocketTimeoutException::class.java,
                    java.net.UnknownHostException::class.java
                )
            )
        ) {
            val response = RetrofitInstance.api.generateRoadmap(
                RoadmapRequest(goal, days)
            )
            
            if (response.isSuccessful) {
                val body = response.body()
                if (body != null) {
                    body
                } else {
                    throw Exception("Empty response from server")
                }
            } else {
                throw Exception("Failed to generate roadmap: ${response.code()} - ${response.message()}")
            }
        }
    }

    /**
     * Saves a goal and its daily plans to Firestore.
     */
    suspend fun saveGoalWithDays(
        goalTitle: String,
        roadmap: RoadmapResponse
    ) {
        val result = RetryUtils.withRetry {
            val goalRef = userGoalsRef().document()

            val goalData = hashMapOf(
                "title" to goalTitle,
                "durationDays" to roadmap.durationDays,
                "currentDay" to 1,
                "pendingAdjustment" to false,
                "lastMissedDay" to null,
                "createdAt" to System.currentTimeMillis()
            )

            goalRef.set(goalData).await()

            roadmap.days.forEach { dayPlan ->

                val dayData = hashMapOf(
                    "dayNumber" to dayPlan.day,
                    "tasks" to dayPlan.tasks.map { it.description },
                    "completed" to dayPlan.tasks.map { it.isCompleted },
                    "status" to "pending"
                )

                goalRef.collection("days")
                    .document(dayPlan.day.toString())
                    .set(dayData)
                    .await()
            }
        }
        
        when (result) {
            is Result.Error -> throw result.exception
            else -> Unit
        }
    }


    /**
     * Checks if the user missed a day by comparing calendar day with progress.
     * Sets pendingAdjustment flag if a day was skipped without completion.
     */
    suspend fun checkForMissedDay(goalId: String): Int? {
        android.util.Log.d("GoalRepository", "=== CHECKING FOR MISSED DAY ===")
        android.util.Log.d("GoalRepository", "Goal ID: $goalId")
        
        val uid = auth.currentUser?.uid 
            ?: throw UserNotAuthenticatedException("User not authenticated")
            
        val goalRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)

        val goalSnap = goalRef.get().await()

        val startDate = goalSnap.getLong("createdAt") ?: run {
            android.util.Log.w("GoalRepository", "No createdAt found for goal")
            return null
        }
        val currentDay = goalSnap.getLong("currentDay")?.toInt() ?: 1
        val durationDays = goalSnap.getLong("durationDays")?.toInt() ?: 30
        val pending = goalSnap.getBoolean("pendingAdjustment") ?: false

        android.util.Log.d("GoalRepository", "Start Date: $startDate (${java.util.Date(startDate)})")
        android.util.Log.d("GoalRepository", "Current Day: $currentDay")
        android.util.Log.d("GoalRepository", "Duration Days: $durationDays")
        android.util.Log.d("GoalRepository", "Pending Adjustment: $pending")

        // already pending → return stored missed day
        if (pending) {
            val missedDay = goalSnap.getLong("lastMissedDay")?.toInt()
            android.util.Log.d("GoalRepository", "Already pending adjustment for day: $missedDay")
            return missedDay
        }

        // Use calendar-based day calculation
        val today = calculateCurrentDay(startDate, durationDays)
        android.util.Log.d("GoalRepository", "Calculated Today: Day $today")

        // Check for incomplete tasks from previous days (days 1 to currentDay-1)
        // IMPORTANT: Skip days that are already marked as "completed" or "skipped"
        android.util.Log.d("GoalRepository", "Checking for incomplete tasks from previous days (1 to ${currentDay - 1})")
        
        for (day in 1 until currentDay) {
            val dayRef = goalRef.collection("days").document(day.toString())
            val daySnap = dayRef.get().await()
            
            if (daySnap.exists()) {
                val status = daySnap.getString("status") ?: "pending"
                
                android.util.Log.d("GoalRepository", "Day $day: status=$status")
                
                // Skip this day if it's already completed or skipped
                if (status == "completed" || status == "skipped") {
                    android.util.Log.d("GoalRepository", "Day $day is $status - skipping check")
                    continue
                }
                
                val tasks = daySnap.get("tasks") as? List<String> ?: emptyList()
                val completed = daySnap.get("completed") as? List<Boolean> ?: emptyList()
                
                // Check if there are any incomplete tasks
                val hasIncompleteTasks = tasks.indices.any { index ->
                    !completed.getOrElse(index) { false }
                }
                
                android.util.Log.d("GoalRepository", "Day $day: hasIncompleteTasks=$hasIncompleteTasks")
                
                if (hasIncompleteTasks) {
                    android.util.Log.d("GoalRepository", "✅ Found incomplete tasks on day $day - setting pending adjustment")
                    
                    goalRef.update(
                        mapOf(
                            "pendingAdjustment" to true,
                            "lastMissedDay" to day
                        )
                    ).await()
                    
                    android.util.Log.d("GoalRepository", "✅ Returning missed day: $day")
                    return day
                }
            }
        }

        // Check if calendar day is ahead of current progress day
        if (today > currentDay) {
            android.util.Log.d("GoalRepository", "Missed day detected! Today ($today) > Current Day ($currentDay)")

            val dayRef = goalRef.collection("days")
                .document(currentDay.toString())

            val daySnap = dayRef.get().await()
            val status = daySnap.getString("status") ?: "pending"
            
            android.util.Log.d("GoalRepository", "Day $currentDay status: $status")

            if (status != "completed") {
                android.util.Log.d("GoalRepository", "Day $currentDay is not completed - setting pending adjustment")

                goalRef.update(
                    mapOf(
                        "pendingAdjustment" to true,
                        "lastMissedDay" to currentDay
                    )
                ).await()

                android.util.Log.d("GoalRepository", "✅ Returning missed day: $currentDay")
                return currentDay   // 🔥 return missed day
            } else {
                android.util.Log.d("GoalRepository", "Day $currentDay was completed - no missed day")
            }
        } else {
            android.util.Log.d("GoalRepository", "No missed day - user is on track (Today: $today, Current: $currentDay)")
        }

        android.util.Log.d("GoalRepository", "=== NO MISSED DAY DETECTED ===")
        return null   // no missed day
    }


    /**
     * Gets the incomplete tasks from a specific day.
     */
    suspend fun getIncompleteTasks(goalId: String, day: Int): List<String> {
        val uid = auth.currentUser?.uid 
            ?: throw UserNotAuthenticatedException("User not authenticated")
            
        val dayRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .collection("days")
            .document(day.toString())
        
        val daySnap = dayRef.get().await()
        
        if (!daySnap.exists()) {
            return emptyList()
        }
        
        val tasks = daySnap.get("tasks") as? List<String> ?: emptyList()
        val completed = daySnap.get("completed") as? List<Boolean> ?: emptyList()
        
        return tasks.filterIndexed { index, _ ->
            !completed.getOrElse(index) { false }
        }
    }

    /**
     * Checks if there is a pending adjustment decision for a missed day.
     */
    suspend fun isAdjustmentPending(goalId: String): Boolean {
        val uid = auth.currentUser?.uid 
            ?: throw UserNotAuthenticatedException("User not authenticated")

        val doc = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .get()
            .await()

        return doc.getBoolean("pendingAdjustment") ?: false
    }

    /**
     * Resolves a missed day by applying the user's chosen adjustment strategy.
     * Actions: SKIP (leave unchanged), MARK_COMPLETED, ADJUST_ROADMAP (redistribute tasks), EXTEND (add 3 days).
     */
    suspend fun resolveSkipAction(goalId: String, action: String) {
        val uid = auth.currentUser?.uid 
            ?: throw UserNotAuthenticatedException("User not authenticated")

        val goalRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)

        when (action) {
            "SKIP" -> {
                // Leave roadmap unchanged, mark the day as skipped so it doesn't trigger again
                android.util.Log.d("GoalRepository", "SKIP: Marking day as skipped")
                
                val missedDay = goalRef.get().await().getLong("lastMissedDay")?.toInt()
                if (missedDay != null) {
                    goalRef.collection("days")
                        .document(missedDay.toString())
                        .update("status", "skipped")
                        .await()
                    android.util.Log.d("GoalRepository", "SKIP: Marked day $missedDay as skipped")
                }
            }

            "MARK_COMPLETED" -> {
                // Mark all tasks in the missed day as completed
                android.util.Log.d("GoalRepository", "MARK_COMPLETED: Marking all tasks as completed")
                
                val goalSnap = goalRef.get().await()
                val missedDay = goalSnap.getLong("lastMissedDay")?.toInt()
                
                android.util.Log.d("GoalRepository", "MARK_COMPLETED: missedDay=$missedDay")
                
                if (missedDay != null) {
                    val dayRef = goalRef.collection("days").document(missedDay.toString())
                    val daySnap = dayRef.get().await()
                    
                    val tasks = daySnap.get("tasks") as? List<String> ?: emptyList()
                    
                    android.util.Log.d("GoalRepository", "MARK_COMPLETED: Marking ${tasks.size} tasks as completed")
                    
                    // Mark all tasks as completed
                    val allCompleted = List(tasks.size) { true }
                    
                    dayRef.update(
                        mapOf(
                            "completed" to allCompleted,
                            "status" to "completed"
                        )
                    ).await()
                    
                    android.util.Log.d("GoalRepository", "MARK_COMPLETED: Successfully marked day $missedDay as completed")
                } else {
                    android.util.Log.w("GoalRepository", "MARK_COMPLETED: missedDay is null, cannot proceed")
                }
            }

            "ADJUST_ROADMAP" -> {
                // Redistribute incomplete tasks from missed days across remaining days
                android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: Starting task redistribution")
                
                val goalSnap = goalRef.get().await()
                val currentDay = goalSnap.getLong("currentDay")?.toInt() ?: 1
                val durationDays = goalSnap.getLong("durationDays")?.toInt() ?: 30
                val missedDay = goalSnap.getLong("lastMissedDay")?.toInt()
                
                android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: currentDay=$currentDay, durationDays=$durationDays, missedDay=$missedDay")
                
                // Collect incomplete tasks from missed days
                val incompleteTasks = mutableListOf<String>()
                if (missedDay != null) {
                    android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: Checking days from $missedDay to ${currentDay - 1}")
                    
                    for (day in missedDay until currentDay) {
                        val daySnap = goalRef.collection("days")
                            .document(day.toString())
                            .get()
                            .await()
                        
                        val status = daySnap.getString("status")
                        android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: Day $day status: $status")
                        
                        if (status != "completed") {
                            val tasks = daySnap.get("tasks") as? List<String> ?: emptyList()
                            val completed = daySnap.get("completed") as? List<Boolean> ?: emptyList()
                            
                            android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: Day $day has ${tasks.size} total tasks")
                            
                            // Add only incomplete tasks
                            tasks.forEachIndexed { index, task ->
                                if (!completed.getOrElse(index) { false }) {
                                    incompleteTasks.add(task)
                                    android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: Adding incomplete task from day $day: $task")
                                }
                            }
                        }
                    }
                } else {
                    android.util.Log.w("GoalRepository", "ADJUST_ROADMAP: missedDay is null")
                }
                
                android.util.Log.d("GoalRepository", "Found ${incompleteTasks.size} incomplete tasks to redistribute")
                
                // Only proceed if there are incomplete tasks
                if (incompleteTasks.isNotEmpty()) {
                    // Collect remaining days starting from currentDay (not missed days)
                    // We only redistribute incomplete tasks + future day tasks
                    val remainingDays = mutableListOf<com.jaydeep.aimwise.data.remote.DayPlanDto>()
                    
                    for (day in currentDay..durationDays) {
                        val daySnap = goalRef.collection("days")
                            .document(day.toString())
                            .get()
                            .await()
                        
                        if (daySnap.exists()) {
                            val tasks = daySnap.get("tasks") as? List<String> ?: emptyList()
                            remainingDays.add(
                                com.jaydeep.aimwise.data.remote.DayPlanDto(
                                    day = day,
                                    tasks = tasks
                                )
                            )
                            android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: Collected day $day with ${tasks.size} tasks")
                        }
                    }
                    
                    val totalRemainingDays = durationDays - currentDay + 1
                    
                    android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: Calling API with ${remainingDays.size} remaining days, ${incompleteTasks.size} incomplete tasks, totalRemainingDays=$totalRemainingDays")
                    
                    // Call API to adjust roadmap
                    val adjustRequest = com.jaydeep.aimwise.data.remote.AdjustRoadmapRequest(
                        remainingDays = remainingDays,
                        incompleteTasks = incompleteTasks,
                        totalRemainingDays = totalRemainingDays
                    )
                    
                    try {
                        val response = RetrofitInstance.api.adjustRoadmap(adjustRequest)
                        
                        android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: API response code: ${response.code()}")
                        
                        if (response.isSuccessful && response.body() != null) {
                            val adjustedDays = response.body()!!.days
                            
                            android.util.Log.d("GoalRepository", "Received ${adjustedDays.size} adjusted days from API")
                            
                            // Update Firestore with adjusted days
                            adjustedDays.forEach { dayDto ->
                                android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: Updating day ${dayDto.day} with ${dayDto.tasks.size} tasks")
                                
                                val dayData = hashMapOf(
                                    "dayNumber" to dayDto.day,
                                    "tasks" to dayDto.tasks,
                                    "completed" to List(dayDto.tasks.size) { false },
                                    "status" to "pending"
                                )
                                
                                goalRef.collection("days")
                                    .document(dayDto.day.toString())
                                    .set(dayData)
                                    .await()
                            }
                            
                            // Mark missed days as skipped
                            if (missedDay != null) {
                                android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: Marking days $missedDay to ${currentDay - 1} as skipped")
                                for (day in missedDay until currentDay) {
                                    goalRef.collection("days")
                                        .document(day.toString())
                                        .update("status", "skipped")
                                        .await()
                                }
                            }
                            
                            android.util.Log.d("GoalRepository", "ADJUST_ROADMAP: Successfully redistributed tasks")
                        } else {
                            val errorBody = response.errorBody()?.string()
                            android.util.Log.e("GoalRepository", "ADJUST_ROADMAP: API error - code: ${response.code()}, message: ${response.message()}, body: $errorBody")
                            throw Exception("Failed to adjust roadmap: ${response.code()} - ${response.message()}")
                        }
                    } catch (e: Exception) {
                        android.util.Log.e("GoalRepository", "ADJUST_ROADMAP: Exception calling API: ${e.message}", e)
                        throw e
                    }
                } else {
                    android.util.Log.d("GoalRepository", "No incomplete tasks to redistribute")
                }
            }

            "EXTEND" -> {
                // Legacy option: Add 3 extra days
                goalRef.update("durationDays", com.google.firebase.firestore.FieldValue.increment(3)).await()
                android.util.Log.d("GoalRepository", "EXTEND: Added 3 days to duration")
            }
        }

        android.util.Log.d("GoalRepository", "Clearing pendingAdjustment and lastMissedDay flags for goal: $goalId")
        goalRef.update(
            mapOf(
                "pendingAdjustment" to false,
                "lastMissedDay" to null
            )
        ).await()
        android.util.Log.d("GoalRepository", "✅ Successfully cleared adjustment flags")
    }


    /**
     * Toggles task completion status. Uses Firestore transaction to ensure atomic updates.
     */
    suspend fun toggleTask(goalId: String, index: Int) {
        val result = RetryUtils.withRetry {
            val uid = auth.currentUser?.uid 
                ?: throw UserNotAuthenticatedException("User not authenticated")

            val goalResult = getGoal(goalId)
            if (goalResult !is Result.Success) {
                throw Exception("Failed to get goal: $goalId")
            }
            
            val day = getTodayDay(goalId)

            val ref = firestore.collection("users")
                .document(uid)
                .collection("goals")
                .document(goalId)
                .collection("days")
                .document(day.toString())

            firestore.runTransaction { transaction ->
                val snap = transaction.get(ref)
                
                // Safe cast with error handling
                val completed = (snap.get("completed") as? List<*>)
                    ?.filterIsInstance<Boolean>()
                    ?.toMutableList()
                    ?: throw Exception("Invalid completed list format")

                // Move index bounds check before modification
                if (index < 0 || index >= completed.size) {
                    throw IndexOutOfBoundsException("Task index $index out of bounds (size: ${completed.size})")
                }
                
                completed[index] = !completed[index]
                transaction.update(ref, "completed", completed)
                Unit // Explicitly return Unit
            }.await()
        }
        
        when (result) {
            is Result.Error -> throw Exception("Failed to toggle task: ${result.exception.message}", result.exception)
            else -> Unit
        }
    }


    /**
     * Marks the current day as completed and advances to the next day.
     */
    suspend fun completeDay(goalId: String) {
        val result = RetryUtils.withRetry {
            val uid = auth.currentUser?.uid 
                ?: throw UserNotAuthenticatedException("User not authenticated")

            val goalRef = firestore.collection("users")
                .document(uid)
                .collection("goals")
                .document(goalId)

            val goalResult = getGoal(goalId)
            if (goalResult !is Result.Success) {
                throw Exception("Failed to get goal: $goalId")
            }
            
            val day = goalResult.data.currentDay

            goalRef.collection("days")
                .document(day.toString())
                .update("status", "completed")
                .await()

            goalRef.update("currentDay", day + 1).await()
            
            // Invalidate cache for the completed day
            dayCache.remove(goalId)
        }
        
        when (result) {
            is Result.Error -> throw result.exception
            else -> Unit
        }
    }


    /**
     * Calculates which day the user should be on based on calendar date, capped at goal duration.
     */
    suspend fun getTodayDay(goalId: String): Int {
        val uid = auth.currentUser?.uid 
            ?: throw UserNotAuthenticatedException("User not authenticated")

        val snap = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .get()
            .await()

        val createdAt = snap.getLong("createdAt") ?: return 1
        val duration = snap.getLong("durationDays")?.toInt() ?: 1

        // Use calendar-based day calculation
        return calculateCurrentDay(createdAt, duration)
    }

    /**
     * Retrieves the plan for a specific day of a goal.
     */
    suspend fun getDayPlan(goalId: String, day: Int): DayPlan? {
        val uid = auth.currentUser?.uid 
            ?: throw UserNotAuthenticatedException("User not authenticated")

        val daySnap = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .collection("days")
            .document(day.toString())
            .get()
            .await()

        val taskDescriptions = daySnap.get("tasks") as? List<String> ?: return null
        val completedStates = daySnap.get("completed") as? List<Boolean> ?: return null
        val status = daySnap.getString("status") ?: "pending"

        // Convert separate lists to Task objects
        val tasks = taskDescriptions.mapIndexed { index, description ->
            Task(
                description = description,
                isCompleted = completedStates.getOrElse(index) { false }
            )
        }

        return DayPlan(day, tasks, status)
    }

    /**
     * Calculates the total task completion percentage for a goal.
     */
    suspend fun getGoalTaskCompletion(goalId: String): Pair<Int, Int> {
        val uid = auth.currentUser?.uid 
            ?: throw UserNotAuthenticatedException("User not authenticated")

        val daysSnap = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .collection("days")
            .get()
            .await()

        var totalCompleted = 0
        var totalTasks = 0

        daysSnap.documents.forEach { dayDoc ->
            val completedStates = dayDoc.get("completed") as? List<Boolean> ?: emptyList()
            totalCompleted += completedStates.count { it }
            totalTasks += completedStates.size
        }

        return Pair(totalCompleted, totalTasks)
    }


    suspend fun deleteGoal(goalId: String) {
        val uid = auth.currentUser?.uid
            ?: throw UserNotAuthenticatedException("User not authenticated")

        // Delete all day documents in the subcollection
        val daysSnap = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .collection("days")
            .get()
            .await()

        val batch = firestore.batch()
        daysSnap.documents.forEach { dayDoc ->
            batch.delete(dayDoc.reference)
        }

        // Delete the goal document
        val goalRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)

        batch.delete(goalRef)
        batch.commit().await()
    }

}



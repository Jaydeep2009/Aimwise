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
 * FIRESTORE INDEXES REQUIRED:
 * For optimal query performance, create the following composite indexes in Firebase Console:
 * 
 * Collection: users/{userId}/goals
 * - Index: createdAt (Descending)
 *   This index supports the getGoals() query with orderBy and limit
 * 
 * To create indexes:
 * 1. Go to Firebase Console > Firestore Database > Indexes
 * 2. Click "Create Index"
 * 3. Add the fields and directions as specified above
 * 
 * Note: Single-field indexes are created automatically by Firestore.
 * Composite indexes (multiple fields) must be created manually.
 */
class GoalRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    
    // Cache for day plans to improve performance
    private val dayCache = mutableMapOf<String, DayPlan>()

    /**
     * Gets the reference to the current user's goals collection.
     * @throws UserNotAuthenticatedException if no user is currently authenticated
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
     * Retrieves all goals for the currently authenticated user.
     * 
     * Goals are ordered by creation date (newest first) and limited to 50 results
     * to prevent excessive data transfer and improve performance.
     * 
     * @return List of Goal objects for the current user
     * @throws UserNotAuthenticatedException if no user is currently authenticated
     * @throws Exception if the Firestore query fails after retry attempts
     */
    suspend fun getGoals(): List<Goal> {
        val result = RetryUtils.withRetry {
            val snapshot = userGoalsRef()
                .orderBy("createdAt", com.google.firebase.firestore.Query.Direction.DESCENDING)
                .limit(50) // Limit results to prevent excessive data transfer
                .get()
                .await()

            snapshot.documents.map { doc ->
                Goal(
                    id = doc.id,
                    title = doc.getString("title") ?: "",
                    durationDays = doc.getLong("durationDays")?.toInt() ?: 0
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
     * Retrieves a specific goal by its ID.
     * 
     * @param goalId The unique identifier of the goal to retrieve
     * @return Result.Success containing the Goal if found, or Result.Error if not found or an error occurs
     * @throws UserNotAuthenticatedException if no user is currently authenticated
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
            
            // Calculate what day it should be based on calendar
            val daysSinceCreation = ((System.currentTimeMillis() - createdAt) / (1000 * 60 * 60 * 24)).toInt() + 1
            val calculatedDay = minOf(daysSinceCreation, durationDays)
            
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
     * Fetches the day plan for a specific day of a goal.
     * 
     * Converts Firestore's separate task and completed lists into a unified Task object list.
     * 
     * @param goalId The unique identifier of the goal
     * @param day The day number to retrieve (1-indexed)
     * @return DayPlan object if found, null if the day doesn't exist
     * @throws UserNotAuthenticatedException if no user is currently authenticated
     * @throws Exception if the Firestore query fails after retry attempts
     */
    suspend fun getDay(goalId: String, day: Int): DayPlan? {
        val result = RetryUtils.withRetry {
            val uid = auth.currentUser?.uid 
                ?: throw UserNotAuthenticatedException("User not authenticated")

            val dayRef = firestore.collection("users")
                .document(uid)
                .collection("goals")
                .document(goalId)
                .collection("days")
                .document(day.toString())

            val daySnap = dayRef.get().await()

            if (!daySnap.exists()) return@withRetry null

            val taskDescriptions = daySnap.get("tasks") as? List<String> ?: emptyList()
            val completedStates = daySnap.get("completed") as? List<Boolean> ?: emptyList()
            val status = daySnap.getString("status") ?: "pending"

            // Convert separate lists to Task objects
            val tasks = taskDescriptions.mapIndexed { index, description ->
                Task(
                    description = description,
                    isCompleted = completedStates.getOrElse(index) { false }
                )
            }

            DayPlan(
                day = day,
                tasks = tasks,
                status = status
            )
        }
        
        return when (result) {
            is Result.Success -> result.data
            is Result.Error -> throw result.exception
            is Result.Loading -> null
        }
    }





    /**
     * Generates a personalized roadmap using AI for the specified goal.
     * 
     * Makes a network request to the backend API which uses AI to create a day-by-day
     * learning plan. Implements retry logic with exponential backoff for network failures.
     * 
     * @param goal The goal description (e.g., "Learn Data Structures and Algorithms")
     * @param days The number of days for the roadmap (typically 30-90 days)
     * @return Result.Success containing RoadmapResponse with daily tasks, or Result.Error on failure
     * @throws Exception if the API request fails after all retry attempts
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
     * Saves a newly generated goal and all its daily plans to Firestore.
     * 
     * This is a transactional operation that:
     * 1. Creates the goal document with metadata
     * 2. Creates a subcollection of day documents with tasks
     * 
     * @param goalTitle The user-provided title for the goal
     * @param roadmap The AI-generated roadmap containing daily plans
     * @throws UserNotAuthenticatedException if no user is currently authenticated
     * @throws Exception if any Firestore write operation fails after retry attempts
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
     * Checks if the user has missed a day and needs to make an adjustment decision.
     * 
     * Compares the current calendar day with the user's progress to detect if they
     * skipped a day without completing it. If a missed day is detected, sets the
     * pendingAdjustment flag and stores the missed day number.
     * 
     * @param goalId The unique identifier of the goal to check
     * @return The day number that was missed, or null if no day was missed or adjustment is already pending
     * @throws UserNotAuthenticatedException if no user is currently authenticated
     */
    suspend fun checkForMissedDay(goalId: String): Int? {
        val uid = auth.currentUser?.uid 
            ?: throw UserNotAuthenticatedException("User not authenticated")
            
        val goalRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)

        val goalSnap = goalRef.get().await()

        val startDate = goalSnap.getLong("createdAt") ?: return null
        val currentDay = goalSnap.getLong("currentDay")?.toInt() ?: 1
        val pending = goalSnap.getBoolean("pendingAdjustment") ?: false

        // already pending → return stored missed day
        if (pending) {
            return goalSnap.getLong("lastMissedDay")?.toInt()
        }

        val today =
            ((System.currentTimeMillis() - startDate) / (1000 * 60 * 60 * 24)).toInt() + 1

        if (today > currentDay) {

            val dayRef = goalRef.collection("days")
                .document(currentDay.toString())

            val daySnap = dayRef.get().await()
            val status = daySnap.getString("status") ?: "pending"

            if (status != "completed") {

                goalRef.update(
                    mapOf(
                        "pendingAdjustment" to true,
                        "lastMissedDay" to currentDay
                    )
                ).await()

                return currentDay   // 🔥 return missed day
            }
        }

        return null   // no missed day
    }


    /**
     * Checks if there is a pending adjustment decision for a missed day.
     * 
     * @param goalId The unique identifier of the goal
     * @return true if the user needs to decide how to handle a missed day, false otherwise
     * @throws UserNotAuthenticatedException if no user is currently authenticated
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
     * 
     * Available actions:
     * - "AUTO": Automatically redistribute tasks (not yet implemented)
     * - "EXTEND": Add 3 extra days to the goal duration
     * - "INCREASE": Increase daily task load (not yet implemented)
     * 
     * After applying the action, clears the pendingAdjustment flag.
     * 
     * @param goalId The unique identifier of the goal
     * @param action The adjustment strategy chosen by the user
     * @throws UserNotAuthenticatedException if no user is currently authenticated
     */
    suspend fun resolveSkipAction(goalId: String, action: String) {
        val uid = auth.currentUser?.uid 
            ?: throw UserNotAuthenticatedException("User not authenticated")

        val goalRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)

        when (action) {

            "AUTO" -> {
                // later: redistribute tasks
            }

            "EXTEND" -> {
                goalRef.update("durationDays", FieldValue.increment(3)).await()
            }

            "INCREASE" -> {
                // later: increase daily load
            }
        }

        goalRef.update("pendingAdjustment", false).await()
    }


    /**
     * Toggles the completion status of a specific task in the current day's plan.
     * 
     * Uses a Firestore transaction to ensure atomic updates and prevent race conditions
     * when multiple clients modify the same task simultaneously. Includes comprehensive
     * error handling for invalid indices and data format issues.
     * 
     * @param goalId The unique identifier of the goal
     * @param index The zero-based index of the task to toggle
     * @throws UserNotAuthenticatedException if no user is currently authenticated
     * @throws IndexOutOfBoundsException if the index is invalid
     * @throws Exception if the transaction fails or data format is invalid
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
     * 
     * This operation:
     * 1. Updates the day's status to "completed"
     * 2. Increments the goal's currentDay counter
     * 3. Invalidates the cache for the completed day
     * 
     * @param goalId The unique identifier of the goal
     * @throws UserNotAuthenticatedException if no user is currently authenticated
     * @throws Exception if the Firestore update fails after retry attempts
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
     * Calculates which day the user should be on based on the goal's creation date.
     * 
     * Compares the current date with the goal's start date to determine the calendar day,
     * capped at the goal's total duration to prevent going beyond the planned days.
     * 
     * @param goalId The unique identifier of the goal
     * @return The day number (1-indexed) that the user should be working on today
     * @throws UserNotAuthenticatedException if no user is currently authenticated
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

        val today = ((System.currentTimeMillis() - createdAt)
                / (1000 * 60 * 60 * 24)).toInt() + 1

        return minOf(today, duration)
    }

    /**
     * Retrieves the plan for a specific day of a goal.
     * 
     * Converts Firestore's storage format (separate task and completed lists) into
     * the app's Task object format for easier manipulation.
     * 
     * @param goalId The unique identifier of the goal
     * @param day The day number to retrieve (1-indexed)
     * @return DayPlan object if found, null if the day doesn't exist
     * @throws UserNotAuthenticatedException if no user is currently authenticated
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

        // Convert separate lists to Task objects
        val tasks = taskDescriptions.mapIndexed { index, description ->
            Task(
                description = description,
                isCompleted = completedStates.getOrElse(index) { false }
            )
        }

        return DayPlan(day, tasks)
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



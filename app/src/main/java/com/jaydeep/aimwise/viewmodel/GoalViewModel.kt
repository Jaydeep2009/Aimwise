package com.jaydeep.aimwise.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydeep.aimwise.data.model.DayPlan
import com.jaydeep.aimwise.data.model.Goal
import com.jaydeep.aimwise.data.model.Result
import com.jaydeep.aimwise.data.repository.GoalRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import com.jaydeep.aimwise.ui.state.ViewState
import java.util.concurrent.atomic.AtomicBoolean

/**
 * ViewModel for managing goal-related state and operations.
 * 
 * Handles:
 * - Loading and displaying goals
 * - Generating AI-powered roadmaps
 * - Managing daily task completion
 * - Detecting and resolving missed days
 * - Optimistic UI updates with rollback on failure
 * 
 * Uses ViewState pattern for consistent error handling and loading states.
 * Implements caching for improved performance and offline support.
 * 
 * @property repo The repository for goal data operations (injected for testability)
 */
class GoalViewModel(
    private val repo: GoalRepository = GoalRepository()
) : ViewModel() {
    private val _goalsState = MutableStateFlow<ViewState<List<Goal>>>(ViewState.Loading)
    val goalsState: StateFlow<ViewState<List<Goal>>> = _goalsState

    private val _roadmapGenerationState = MutableStateFlow<ViewState<Unit>>(ViewState.Success(Unit))
    val roadmapGenerationState: StateFlow<ViewState<Unit>> = _roadmapGenerationState

    private val isGenerating = AtomicBoolean(false)   // 🔒 prevents duplicate calls with thread-safe operations

    private val _pendingAdjustment = MutableStateFlow(false)
    val pendingAdjustment: StateFlow<Boolean> = _pendingAdjustment

    private val _missedDay = MutableStateFlow<Int?>(null)
    val missedDay: StateFlow<Int?> = _missedDay

    private val _goalState = MutableStateFlow<ViewState<Goal>>(ViewState.Loading)
    val goalState: StateFlow<ViewState<Goal>> = _goalState

    private val _dayPlanState = MutableStateFlow<ViewState<DayPlan>>(ViewState.Loading)
    val dayPlanState: StateFlow<ViewState<DayPlan>> = _dayPlanState

    private val _currentDay = MutableStateFlow<Int?>(null)
    val currentDay: StateFlow<Int?> = _currentDay


    private val dayCache = mutableMapOf<String, DayPlan>()


    /**
     * Resets all state flows to their initial values.
     * 
     * Called when navigating to a new goal or refreshing the current view.
     * Ensures clean state and prevents stale data from being displayed.
     */
    fun resetState() {
        _goalState.value = ViewState.Loading
        _dayPlanState.value = ViewState.Loading
        _currentDay.value = null
        _pendingAdjustment.value = false
        _missedDay.value = null
    }

    /**
     * Resets the roadmap generation state to allow new generation requests.
     * 
     * Must be called when the generation dialog is dismissed to prevent
     * showing stale success/error states on the next generation attempt.
     */
    fun resetDoneState() {
        _roadmapGenerationState.value = ViewState.Success(Unit)
    }
    /**
     * Checks if the user has missed a day and needs to make an adjustment decision.
     * 
     * Updates pendingAdjustment and missedDay state flows based on the result.
     * The UI can observe these states to display the adjustment dialog.
     * 
     * @param goalId The unique identifier of the goal to check
     */
    fun checkMissedDay(goalId: String) {
        viewModelScope.launch {
            val day = repo.checkForMissedDay(goalId)

            if (day != null) {
                _pendingAdjustment.value = true
                _missedDay.value = day
            } else {
                _pendingAdjustment.value = false
            }
        }
    }

    fun checkPending(goalId: String) {
        viewModelScope.launch {
            _pendingAdjustment.value = repo.isAdjustmentPending(goalId)
        }
    }


    /**
     * Loads all goals for the current user.
     * 
     * Validates that a user is authenticated before making the API call.
     * Updates goalsState with Loading, Success, or Error states.
     * The UI observes this state to display the goals list or error messages.
     */
    fun loadGoals() {
        viewModelScope.launch {
            _goalsState.value = ViewState.Loading
            
            // Validate user is authenticated before making API call
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            android.util.Log.d("AUTH_DEBUG", "Loading goals. User: ${user?.uid ?: "NULL"}")
            android.util.Log.d("GOAL_VIEWMODEL", "loadGoals - User: ${user?.uid ?: "NULL"}")
            
            if (user == null) {
                android.util.Log.e("GOAL_VIEWMODEL", "⚠️ User not authenticated in loadGoals")
                _goalsState.value = ViewState.Error(
                    message = "User not authenticated. Please login again.",
                    throwable = null
                )
                return@launch
            }
            
            try {
                val goals = repo.getGoals()
                android.util.Log.d("GOAL_VIEWMODEL", "✅ Loaded ${goals.size} goals")
                _goalsState.value = ViewState.Success(goals)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("GOAL_VIEWMODEL", "❌ Failed to load goals: ${e.message}")
                _goalsState.value = ViewState.Error(
                    message = e.message ?: "Failed to load goals",
                    throwable = e
                )
            }
        }
    }

    /**
     * Generates a new goal with an AI-powered roadmap and saves it to Firestore.
     * 
     * Uses atomic boolean to prevent duplicate generation requests if the user
     * clicks multiple times. Updates roadmapGenerationState to reflect progress:
     * - Loading: Generation in progress
     * - Success: Goal created and saved successfully
     * - Error: Generation or save failed with error message
     * 
     * Handles network errors gracefully with user-friendly messages.
     * 
     * @param title The goal title provided by the user
     * @param days The number of days for the roadmap (typically 30-90)
     */
    fun generateGoalWithRoadmap(title: String, days: Int) {

        if (!isGenerating.compareAndSet(false, true)) return   // 🔒 block duplicates immediately with atomic operation

        viewModelScope.launch {
            _roadmapGenerationState.value = ViewState.Loading
            
            // Validate user is authenticated before making API call
            val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
            android.util.Log.d("GOAL_VIEWMODEL", "generateRoadmap - User: ${user?.uid ?: "NULL"}")
            
            if (user == null) {
                android.util.Log.e("GOAL_VIEWMODEL", "⚠️ User not authenticated in generateRoadmap")
                _roadmapGenerationState.value = ViewState.Error(
                    message = "User not authenticated. Please login again.",
                    throwable = null
                )
                isGenerating.set(false)
                return@launch
            }
            
            try {
                val roadmapResult = repo.generateRoadmap(title, days)

                when (roadmapResult) {
                    is com.jaydeep.aimwise.data.model.Result.Success -> {
                        repo.saveGoalWithDays(title, roadmapResult.data)
                        loadGoals()
                        _roadmapGenerationState.value = ViewState.Success(Unit)
                    }
                    is com.jaydeep.aimwise.data.model.Result.Error -> {
                        val errorMessage = getNetworkErrorMessage(roadmapResult.exception)
                        android.util.Log.e("GOAL_VIEWMODEL", "❌ Roadmap generation failed: $errorMessage")
                        roadmapResult.exception.printStackTrace()
                        _roadmapGenerationState.value = ViewState.Error(
                            message = errorMessage,
                            throwable = roadmapResult.exception
                        )
                    }
                    is com.jaydeep.aimwise.data.model.Result.Loading -> {
                        // Loading state already set
                    }
                }

            } catch (e: CancellationException) {
                // Coroutine was cancelled (user navigated away) - don't log as error
                throw e
            } catch (e: Exception) {
                val errorMessage = getNetworkErrorMessage(e)
                android.util.Log.e("GOAL_VIEWMODEL", "❌ Roadmap generation exception: $errorMessage")
                e.printStackTrace()
                _roadmapGenerationState.value = ViewState.Error(
                    message = errorMessage,
                    throwable = e
                )
            } finally {
                isGenerating.set(false)
            }
        }
    }

    /**
     * Converts technical exceptions into user-friendly error messages.
     * 
     * Detects common network issues and provides actionable guidance.
     */
    private fun getNetworkErrorMessage(exception: Exception): String {
        return when {
            exception is java.net.UnknownHostException -> 
                "No internet connection. Please check your network and try again."
            
            exception is java.net.SocketTimeoutException -> 
                "Request timed out. Please check your internet connection and try again."
            
            exception is java.io.IOException && exception.message?.contains("Unable to resolve host") == true -> 
                "Cannot reach server. Please check your internet connection."
            
            exception is java.io.IOException -> 
                "Network error. Please check your internet connection and try again."
            
            exception.message?.contains("timeout", ignoreCase = true) == true -> 
                "Connection timed out. Please check your internet and try again."
            
            exception.message?.contains("network", ignoreCase = true) == true -> 
                "Network error. Please check your internet connection."
            
            exception.message?.contains("Unable to resolve host", ignoreCase = true) == true -> 
                "Cannot reach server. Please check your internet connection."
            
            else -> exception.message ?: "Failed to generate roadmap. Please try again."
        }
    }

    /**
     * Applies the user's chosen adjustment strategy for a missed day.
     * 
     * After resolving, clears the pendingAdjustment and missedDay states
     * so the adjustment dialog is dismissed.
     * 
     * @param goalId The unique identifier of the goal
     * @param action The adjustment strategy: "AUTO", "EXTEND", or "INCREASE"
     */
    fun resolveSkip(goalId: String, action: String) {
        viewModelScope.launch {
            repo.resolveSkipAction(goalId, action)

            _pendingAdjustment.value = false
            _missedDay.value = null
        }
    }

    /**
     * Loads the roadmap for a specific goal, including goal details and today's plan.
     * 
     * Implements a multi-step loading process:
     * 1. Resets state to clear previous data
     * 2. Checks cache for instant display (if available)
     * 3. Fetches goal details from Firestore
     * 4. Calculates which day the user should be on
     * 5. Loads the day plan and updates cache
     * 
     * Updates goalState, dayPlanState, and currentDay as data becomes available.
     * 
     * @param goalId The unique identifier of the goal to load
     */
    fun loadRoadmap(goalId: String) {
        viewModelScope.launch {
            resetState()

            // 🟢 instant cache (if exists)
            val cached = dayCache[goalId]
            if (cached != null) {
                _dayPlanState.value = ViewState.Success(cached)
            }

            // 🟢 fetch goal
            val goalResult = repo.getGoal(goalId)
            when (goalResult) {
                is com.jaydeep.aimwise.data.model.Result.Success -> {
                    _goalState.value = ViewState.Success(goalResult.data)
                }
                is com.jaydeep.aimwise.data.model.Result.Error -> {
                    goalResult.exception.printStackTrace()
                    _goalState.value = ViewState.Error(
                        message = goalResult.exception.message ?: "Failed to load goal",
                        throwable = goalResult.exception
                    )
                    return@launch
                }
                is com.jaydeep.aimwise.data.model.Result.Loading -> {
                    // Loading state already set
                }
            }

            // 🟢 calculate today
            val todayDay = repo.getTodayDay(goalId)
            _currentDay.value = todayDay

            // 🟢 fetch day from DB
            val dayPlan = repo.getDayPlan(goalId, todayDay)
            if (dayPlan != null) {
                _dayPlanState.value = ViewState.Success(dayPlan)
                dayCache[goalId] = dayPlan   // cache it
            } else {
                _dayPlanState.value = ViewState.Error(
                    message = "Day plan not found",
                    throwable = null
                )
            }
        }
    }



    /**
     * Toggles the completion status of a task with optimistic UI updates.
     * 
     * Immediately updates the UI to reflect the toggle, then syncs with Firestore
     * in the background. If the Firestore update fails, rolls back to the previous
     * state to maintain consistency.
     * 
     * This pattern provides instant feedback to the user while ensuring data integrity.
     * 
     * @param goalId The unique identifier of the goal
     * @param index The zero-based index of the task to toggle
     */
    fun toggleTask(goalId: String, index: Int) {

        val currentState = _dayPlanState.value
        if (currentState !is ViewState.Success) return
        
        val current = currentState.data

        val updated = current.copy(
            tasks = current.tasks.toMutableList().apply {
                this[index] = this[index].copy(isCompleted = !this[index].isCompleted)
            }
        )

        // 🔥 instant UI update (optimistic)
        _dayPlanState.value = ViewState.Success(updated)
        dayCache[goalId] = updated

        // 🔵 background firestore sync with rollback on failure
        viewModelScope.launch {
            try {
                repo.toggleTask(goalId, index)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // 🔴 Rollback on failure - restore previous state
                e.printStackTrace()
                _dayPlanState.value = ViewState.Success(current)
                dayCache[goalId] = current
            }
        }
    }





    /**
     * Marks the current day as complete and advances to the next day.
     * 
     * Updates the UI immediately with the next day number, then syncs with
     * Firestore and loads the next day's plan in the background.
     * 
     * @param goalId The unique identifier of the goal
     */
    fun completeDay(goalId: String) {

        val goalStateValue = _goalState.value
        if (goalStateValue !is ViewState.Success) return
        
        val goalData = goalStateValue.data
        val nextDay = goalData.currentDay + 1

        _goalState.value = ViewState.Success(goalData.copy(currentDay = nextDay))
        _currentDay.value = nextDay

        viewModelScope.launch {
            repo.completeDay(goalId)
            val newPlan = repo.getDayPlan(goalId, nextDay)
            if (newPlan != null) {
                _dayPlanState.value = ViewState.Success(newPlan)
            } else {
                _dayPlanState.value = ViewState.Error(
                    message = "Day plan not found",
                    throwable = null
                )
            }
        }
    }



    fun loadToday(goalId: String) {
        viewModelScope.launch {
            val day = repo.getTodayDay(goalId)
            _currentDay.value = day
        }
    }

    /**
     * Gets the total task completion for a goal.
     * 
     * @param goalId The unique identifier of the goal
     * @return Pair of (completed tasks count, total tasks count)
     */
    suspend fun getGoalTaskCompletion(goalId: String): Pair<Int, Int> {
        return repo.getGoalTaskCompletion(goalId)
    }

    fun loadDay(goalId: String, day: Int) {
        viewModelScope.launch {
            _dayPlanState.value = ViewState.Loading
            val dayPlan = repo.getDayPlan(goalId, day)
            if (dayPlan != null) {
                _dayPlanState.value = ViewState.Success(dayPlan)
            } else {
                _dayPlanState.value = ViewState.Error(
                    message = "Day plan not found",
                    throwable = null
                )
            }
        }
    }

    /**
     * Gets the day plan for a specific day without updating the state.
     * Used for loading all days in the full roadmap view.
     * 
     * @param goalId The unique identifier of the goal
     * @param day The day number to retrieve
     * @return DayPlan object if found, null otherwise
     */
    suspend fun getDayPlanForDay(goalId: String, day: Int): DayPlan? {
        return repo.getDayPlan(goalId, day)
    }

    fun deleteGoal(goalId: String) {
        viewModelScope.launch {
            try {
                repo.deleteGoal(goalId)
                loadGoals()  // Refresh the goals list after deletion
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                e.printStackTrace()
                // Optionally, you could update a state flow here to show an error message in the UI
            }
        }
    }



}

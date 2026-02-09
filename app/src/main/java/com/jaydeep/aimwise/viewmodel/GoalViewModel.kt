package com.jaydeep.aimwise.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydeep.aimwise.data.model.DayPlan
import com.jaydeep.aimwise.data.model.Goal
import com.jaydeep.aimwise.data.repository.GoalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class GoalViewModel : ViewModel() {

    private val repo = GoalRepository()

    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done

    private var isGenerating = false   // 🔒 prevents duplicate calls

    private val _pendingAdjustment = MutableStateFlow(false)
    val pendingAdjustment: StateFlow<Boolean> = _pendingAdjustment

    private val _missedDay = MutableStateFlow<Int?>(null)
    val missedDay: StateFlow<Int?> = _missedDay

    private val _goal = MutableStateFlow<Goal?>(null)
    val goal: StateFlow<Goal?> = _goal

    private val _dayPlan = MutableStateFlow<DayPlan?>(null)
    val dayPlan: StateFlow<DayPlan?> = _dayPlan

    private val _currentDay = MutableStateFlow<Int?>(null)
    val currentDay: StateFlow<Int?> = _currentDay



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


    fun loadGoals() {
        viewModelScope.launch {
            _goals.value = repo.getGoals()
        }
    }

    fun generateGoalWithRoadmap(title: String, days: Int) {

        if (isGenerating) return   // 🔒 block duplicates immediately
        isGenerating = true

        viewModelScope.launch {
            try {
                val roadmap = repo.generateRoadmap(title, days)

                if (roadmap != null) {
                    repo.saveGoalWithDays(title, roadmap)
                    loadGoals()
                }

            } catch (e: Exception) {
                e.printStackTrace()
            }

            _done.value = true
            isGenerating = false
        }
    }

    fun resolveSkip(goalId: String, action: String) {
        viewModelScope.launch {
            repo.resolveSkipAction(goalId, action)

            _pendingAdjustment.value = false
            _missedDay.value = null
        }
    }

    fun loadRoadmap(goalId: String) {
        viewModelScope.launch {

            val goal = repo.getGoal(goalId)
            if (goal == null) return@launch

            // 🔵 calculate today's day automatically
            val todayDay = repo.getTodayDay(goalId)

            _currentDay.value = todayDay

            // 🔵 load today's tasks
            val dayPlan = repo.getDayPlan(goalId, todayDay)
            _dayPlan.value = dayPlan
        }
    }


    fun toggleTask(goalId: String, index: Int) {
        viewModelScope.launch {
            repo.toggleTask(goalId, index)
            loadRoadmap(goalId)
        }
    }

    fun completeDay(goalId: String) {
        viewModelScope.launch {
            repo.completeDay(goalId)
            loadRoadmap(goalId)
        }
    }

    fun loadToday(goalId: String) {
        viewModelScope.launch {
            val day = repo.getTodayDay(goalId)
            _currentDay.value = day
        }
    }

    fun loadDay(goalId: String, day: Int) {
        viewModelScope.launch {
            _dayPlan.value = repo.getDayPlan(goalId, day)
        }
    }


}

package com.jaydeep.aimwise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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


    fun loadGoals() {
        viewModelScope.launch {
            _goals.value = repo.getGoals()
        }
    }

    // 🟢 AI-based goal creation
    fun generateGoalWithRoadmap(title: String, days: Int) {
        if (_done.value) return   // prevent multiple calls

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
        }
    }
}

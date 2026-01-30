package com.jaydeep.aimwise.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydeep.aimwise.data.model.Goal
import com.jaydeep.aimwise.data.repository.GoalRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch


class GoalViewModel: ViewModel() {
    private val repo = GoalRepository()
    private val _goals = MutableStateFlow<List<Goal>>(emptyList())
    val goals: StateFlow<List<Goal>> = _goals

    fun loadGoals(){
        viewModelScope.launch{
            _goals.value = repo.getGoals()
        }
    }

    fun addGoal(title:String){
        viewModelScope.launch {
            repo.addGoal(title)
            loadGoals() // refresh list
        }
    }
}
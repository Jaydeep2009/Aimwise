package com.jaydeep.aimwise.data.model

data class DayPlan(
    val day: Int = 0,
    val tasks: List<String> = emptyList(),
    val completed: List<Boolean> = emptyList(),
    val status: String = "pending"   // important for skip feature
)
package com.jaydeep.aimwise.data.model

data class RoadmapResponse(
    val title: String,
    val durationDays: Int,
    val days: List<DayPlan>
)

data class DayPlan(
    val day: Int,
    val tasks: List<String>
)

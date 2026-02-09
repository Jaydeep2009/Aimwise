package com.jaydeep.aimwise.data.model

data class RoadmapResponse(
    val title: String,
    val durationDays: Int,
    val days: List<DayPlan>
)




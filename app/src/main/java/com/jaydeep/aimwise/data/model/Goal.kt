package com.jaydeep.aimwise.data.model

/**
 * Represents a user's goal with associated roadmap and progress tracking.
 * 
 * @property id Unique identifier (Firestore document ID)
 * @property title Goal description entered by the user
 * @property roadmap AI-generated roadmap in JSON format
 * @property createdAt Unix timestamp (milliseconds) when goal was created
 * @property durationDays Total days to complete the goal (default: 30)
 * @property currentDay Current day in goal progress (1-indexed)
 * @property completedTasks Number of completed tasks across all days
 * @property totalTasks Total number of tasks across all days
 * @property todayCompletedTasks Number of completed tasks for current day
 * @property todayTotalTasks Total number of tasks for current day
 */
data class Goal(
    val id: String = "",
    val title: String = "",
    val roadmap: String = "",
    val createdAt: Long = 0L,
    val durationDays: Int = 30,
    val currentDay: Int = 1,
    val completedTasks: Int = 0,
    val totalTasks: Int = 0,
    val todayCompletedTasks: Int = 0,
    val todayTotalTasks: Int = 0
)

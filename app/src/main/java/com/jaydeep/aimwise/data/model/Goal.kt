package com.jaydeep.aimwise.data.model

/**
 * Represents a user's goal with associated roadmap and progress tracking.
 * 
 * This data class models a goal that a user wants to achieve, including
 * the AI-generated roadmap, duration, and current progress.
 * 
 * @property id Unique identifier for the goal (Firestore document ID).
 *              Empty string for new goals before Firestore assignment.
 * @property title The goal description entered by the user.
 *                 Must not be blank (validated by [validate] method).
 * @property roadmap AI-generated roadmap content in JSON format.
 *                   Contains the structured plan for achieving the goal.
 *                   Empty string if roadmap generation is pending.
 * @property createdAt Unix timestamp (milliseconds) when the goal was created.
 *                     Defaults to 0L for new goals before persistence.
 * @property durationDays Total number of days allocated to complete the goal.
 *                        Must be positive (≥ 1). Defaults to 30 days.
 *                        Valid range: 1 to Integer.MAX_VALUE.
 * @property currentDay The current day number in the goal's progress (1-indexed).
 *                      Must be positive (≥ 1) and not exceed [durationDays].
 *                      Defaults to 1 (first day). Valid range: 1 to [durationDays].
 */
data class Goal(
    val id: String = "",
    val title: String = "",
    val roadmap: String = "",
    val createdAt: Long = 0L,
    val durationDays: Int = 30,
    val currentDay: Int = 1
) {
    /**
     * Validates the Goal data integrity.
     * 
     * @return Result.Success if all validations pass, Result.Error with exception otherwise
     */
    fun validate(): Result<Unit> {
        return when {
            title.isBlank() -> Result.Error(Exception("Title cannot be blank"))
            durationDays < 1 -> Result.Error(Exception("Duration must be positive"))
            currentDay < 1 -> Result.Error(Exception("Current day must be positive"))
            currentDay > durationDays -> Result.Error(Exception("Current day cannot exceed duration"))
            else -> Result.Success(Unit)
        }
    }
}

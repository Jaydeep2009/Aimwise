package com.jaydeep.aimwise.data.model


data class Goal(
    val id: String = "",          // Firestore document ID
    val title: String = "",       // Goal text
    val roadmap: String = "",     // AI-generated roadmap (later)
    val createdAt: Long = 0L,      // Timestamp
    val durationDays: Int
)

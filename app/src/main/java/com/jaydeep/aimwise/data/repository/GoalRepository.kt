package com.jaydeep.aimwise.data.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.jaydeep.aimwise.data.model.Goal
import com.jaydeep.aimwise.data.model.RoadmapResponse
import com.jaydeep.aimwise.data.remote.RetrofitInstance
import com.jaydeep.aimwise.data.remote.RoadmapRequest
import kotlinx.coroutines.tasks.await

class GoalRepository {

    private val firestore = FirebaseFirestore.getInstance()

    // 🟢 GET ALL GOALS
    suspend fun getGoals(): List<Goal> {
        val snapshot = firestore.collection("goals").get().await()

        return snapshot.documents.map { doc ->
            Goal(
                id = doc.id,
                title = doc.getString("title") ?: "",
                durationDays = doc.getLong("durationDays")?.toInt() ?: 0
            )
        }
    }

    // 🟢 CALL BACKEND → GEMINI
    suspend fun generateRoadmap(
        goal: String,
        days: Int
    ): RoadmapResponse? {

        val response = RetrofitInstance.api.generateRoadmap(
            RoadmapRequest(goal, days)
        )

        if (response.isSuccessful) {
            return response.body()
        }

        return null
    }

    // 🟢 SAVE GOAL + DAYS TO FIRESTORE
    suspend fun saveGoalWithDays(
        goalTitle: String,
        roadmap: RoadmapResponse
    ) {

        val goalRef = firestore.collection("goals").document()

        val goalData = hashMapOf(
            "title" to goalTitle,
            "durationDays" to roadmap.durationDays
        )

        goalRef.set(goalData).await()

        roadmap.days.forEach { dayPlan ->

            val dayData = hashMapOf(
                "dayNumber" to dayPlan.day,
                "tasks" to dayPlan.tasks,
                "completed" to List(dayPlan.tasks.size) { false }
            )

            goalRef.collection("days")
                .document(dayPlan.day.toString())
                .set(dayData)
                .await()
        }
    }
}

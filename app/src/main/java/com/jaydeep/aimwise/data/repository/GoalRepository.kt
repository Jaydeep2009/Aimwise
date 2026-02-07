package com.jaydeep.aimwise.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jaydeep.aimwise.data.model.Goal
import com.jaydeep.aimwise.data.model.RoadmapResponse
import com.jaydeep.aimwise.data.remote.RetrofitInstance
import com.jaydeep.aimwise.data.remote.RoadmapRequest
import kotlinx.coroutines.tasks.await

class GoalRepository {

    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun userGoalsRef() =
        firestore.collection("users")
            .document(auth.currentUser!!.uid)
            .collection("goals")

    // 🟢 GET GOALS
    suspend fun getGoals(): List<Goal> {
        val snapshot = userGoalsRef().get().await()

        return snapshot.documents.map { doc ->
            Goal(
                id = doc.id,
                title = doc.getString("title") ?: "",
                durationDays = doc.getLong("durationDays")?.toInt() ?: 0
            )
        }
    }

    // 🟢 GENERATE ROADMAP
    suspend fun generateRoadmap(goal: String, days: Int): RoadmapResponse? {
        val response = RetrofitInstance.api.generateRoadmap(
            RoadmapRequest(goal, days)
        )
        if (response.isSuccessful) return response.body()
        return null
    }

    // 🟢 SAVE GOAL + DAYS
    suspend fun saveGoalWithDays(
        goalTitle: String,
        roadmap: RoadmapResponse
    ) {
        val goalRef = userGoalsRef().document()

        val goalData = hashMapOf(
            "title" to goalTitle,
            "durationDays" to roadmap.durationDays,
            "currentDay" to 1,
            "pendingAdjustment" to false,
            "lastMissedDay" to null,
            "createdAt" to System.currentTimeMillis()
        )

        goalRef.set(goalData).await()

        roadmap.days.forEach { dayPlan ->

            val dayData = hashMapOf(
                "dayNumber" to dayPlan.day,
                "tasks" to dayPlan.tasks,
                "completed" to List(dayPlan.tasks.size) { false },
                "status" to "pending"
            )

            goalRef.collection("days")
                .document(dayPlan.day.toString())
                .set(dayData)
                .await()
        }
    }


    suspend fun checkForMissedDay(goalId: String) {
        val uid = auth.currentUser!!.uid
        val goalRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)

        val goalSnap = goalRef.get().await()

        val startDate = goalSnap.getLong("createdAt") ?: return
        val currentDay = goalSnap.getLong("currentDay")?.toInt() ?: 1
        val pending = goalSnap.getBoolean("pendingAdjustment") ?: false

        if (pending) return

        val today =
            ((System.currentTimeMillis() - startDate) / (1000 * 60 * 60 * 24)).toInt() + 1

        if (today > currentDay) {
            val dayRef = goalRef.collection("days")
                .document(currentDay.toString())

            val daySnap = dayRef.get().await()
            val status = daySnap.getString("status") ?: "pending"

            if (status != "completed") {
                goalRef.update(
                    mapOf(
                        "pendingAdjustment" to true,
                        "lastMissedDay" to currentDay
                    )
                ).await()
            }
        }
    }

    suspend fun isAdjustmentPending(goalId: String): Boolean {
        val uid = auth.currentUser!!.uid

        val doc = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .get()
            .await()

        return doc.getBoolean("pendingAdjustment") ?: false
    }


}



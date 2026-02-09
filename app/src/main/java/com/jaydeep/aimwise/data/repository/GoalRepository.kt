package com.jaydeep.aimwise.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.jaydeep.aimwise.data.model.DayPlan
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
//get goal by id
    suspend fun getGoal(goalId: String): Goal? {
        val uid = auth.currentUser!!.uid

        val doc = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .get()
            .await()

        return if (doc.exists()) {
            Goal(
                id = doc.id,
                title = doc.getString("title") ?: "",
                durationDays = doc.getLong("durationDays")?.toInt() ?: 0,
                currentDay = doc.getLong("currentDay")?.toInt() ?: 1
            )
        } else null
    }
//fetch day tasks
    suspend fun getDay(goalId: String, day: Int): DayPlan? {
        val uid = auth.currentUser!!.uid

        val dayRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .collection("days")
            .document(day.toString())

        val daySnap = dayRef.get().await()

        if (!daySnap.exists()) return null

        val tasks = daySnap.get("tasks") as? List<String> ?: emptyList()
        val completed = daySnap.get("completed") as? List<Boolean> ?: emptyList()
        val status = daySnap.getString("status") ?: "pending"

        return DayPlan(
            day = day,
            tasks = tasks,
            completed = completed,
            status = status
        )
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


    suspend fun checkForMissedDay(goalId: String): Int? {

        val uid = auth.currentUser!!.uid
        val goalRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)

        val goalSnap = goalRef.get().await()

        val startDate = goalSnap.getLong("createdAt") ?: return null
        val currentDay = goalSnap.getLong("currentDay")?.toInt() ?: 1
        val pending = goalSnap.getBoolean("pendingAdjustment") ?: false

        // already pending → return stored missed day
        if (pending) {
            return goalSnap.getLong("lastMissedDay")?.toInt()
        }

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

                return currentDay   // 🔥 return missed day
            }
        }

        return null   // no missed day
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

    suspend fun resolveSkipAction(goalId: String, action: String) {

        val uid = auth.currentUser!!.uid

        val goalRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)

        when (action) {

            "AUTO" -> {
                // later: redistribute tasks
            }

            "EXTEND" -> {
                goalRef.update("durationDays", FieldValue.increment(3)).await()
            }

            "INCREASE" -> {
                // later: increase daily load
            }
        }

        goalRef.update("pendingAdjustment", false).await()
    }


    suspend fun toggleTask(goalId: String, index: Int) {
        val uid = auth.currentUser!!.uid

        val goal = getGoal(goalId) ?: return
        val day = goal.currentDay

        val ref = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .collection("days")
            .document(day.toString())

        val snap = ref.get().await()

        val completed = (snap.get("completed") as List<Boolean>).toMutableList()
        completed[index] = !completed[index]

        ref.update("completed", completed).await()
    }

    suspend fun completeDay(goalId: String) {
        val uid = auth.currentUser!!.uid

        val goalRef = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)

        val goal = getGoal(goalId) ?: return
        val day = goal.currentDay

        goalRef.collection("days")
            .document(day.toString())
            .update("status", "completed")
            .await()

        goalRef.update("currentDay", day + 1).await()
    }


    suspend fun getTodayDay(goalId: String): Int {
        val uid = auth.currentUser!!.uid

        val snap = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .get()
            .await()

        val createdAt = snap.getLong("createdAt") ?: return 1
        val duration = snap.getLong("durationDays")?.toInt() ?: 1

        val today = ((System.currentTimeMillis() - createdAt)
                / (1000 * 60 * 60 * 24)).toInt() + 1

        return minOf(today, duration)
    }

    suspend fun getDayPlan(goalId: String, day: Int): DayPlan? {
        val uid = auth.currentUser!!.uid

        val daySnap = firestore.collection("users")
            .document(uid)
            .collection("goals")
            .document(goalId)
            .collection("days")
            .document(day.toString())
            .get()
            .await()

        val tasks = daySnap.get("tasks") as? List<String> ?: return null
        val completed = daySnap.get("completed") as? List<Boolean> ?: return null

        return DayPlan(day, tasks, completed)
    }




}



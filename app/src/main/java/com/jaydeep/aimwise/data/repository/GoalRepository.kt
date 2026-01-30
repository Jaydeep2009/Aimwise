package com.jaydeep.aimwise.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.jaydeep.aimwise.data.model.Goal
import kotlinx.coroutines.tasks.await

class GoalRepository {
    private val firestore = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    private fun userGoalRef() =
        firestore.collection("users")
            .document(auth.currentUser?.uid ?: "")
            .collection("goals")

    suspend fun addGoal(title: String) {
       val goalRef = userGoalRef().document()
        val goalData = Goal(
            id = goalRef.id,
            title= title,
            roadmap="",
            createdAt = System.currentTimeMillis()
        )
        goalRef.set(goalData).await()
    }

    suspend fun getGoals(): List<Goal> {
        val snapshot = userGoalRef()
            .orderBy("createdAt")
            .get()
            .await()
            .documents
        return snapshot.mapNotNull { it.toObject(Goal::class.java) }
    }
}
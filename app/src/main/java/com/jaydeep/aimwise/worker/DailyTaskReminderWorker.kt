package com.jaydeep.aimwise.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import com.jaydeep.aimwise.MainActivity
import com.jaydeep.aimwise.R
import com.jaydeep.aimwise.data.repository.GoalRepository

class DailyTaskReminderWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val repository = GoalRepository()
    private val auth = FirebaseAuth.getInstance()

    override suspend fun doWork(): Result {
        return try {
            Log.d(TAG, "Worker started")
            
            // Check if user is logged in
            val user = auth.currentUser
            if (user == null) {
                Log.d(TAG, "No user logged in")
                return Result.success()
            }
            
            Log.d(TAG, "User logged in: ${user.uid}")

            // Get all goals
            val goals = repository.getGoals()
            Log.d(TAG, "Found ${goals.size} goals")
            
            // Check each goal for incomplete tasks today
            val pendingGoals = mutableListOf<String>()
            
            for (goal in goals) {
                val currentDay = repository.getTodayDay(goal.id)
                Log.d(TAG, "Goal: ${goal.title}, Current day: $currentDay")
                
                val dayPlan = repository.getDayPlan(goal.id, currentDay)
                
                if (dayPlan != null) {
                    val hasIncompleteTasks = dayPlan.tasks.any { !it.isCompleted }
                    Log.d(TAG, "Goal: ${goal.title}, Has incomplete tasks: $hasIncompleteTasks")
                    
                    if (hasIncompleteTasks) {
                        pendingGoals.add(goal.title)
                    }
                }
            }

            Log.d(TAG, "Pending goals: ${pendingGoals.size}")
            
            // Send notification if there are pending goals
            if (pendingGoals.isNotEmpty()) {
                Log.d(TAG, "Sending notification for: ${pendingGoals.joinToString()}")
                sendNotification(pendingGoals)
            } else {
                Log.d(TAG, "No pending goals, skipping notification")
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Worker failed", e)
            e.printStackTrace()
            Result.failure()
        }
    }

    private fun sendNotification(pendingGoals: List<String>) {
        Log.d(TAG, "Creating notification")
        val notificationManager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Create notification channel for Android O and above
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Daily Task Reminders",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Reminders for incomplete daily tasks"
                enableVibration(true)
                enableLights(true)
            }
            notificationManager.createNotificationChannel(channel)
            Log.d(TAG, "Notification channel created")
        }

        // Create intent to open MainActivity
        val intent = Intent(applicationContext, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            applicationContext,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Build notification content
        val (title, text) = if (pendingGoals.size == 1) {
            "Finish today's tasks" to "${pendingGoals[0]} is incomplete"
        } else {
            "You have pending tasks today" to pendingGoals.joinToString("\n") { "• $it" }
        }

        Log.d(TAG, "Notification title: $title")
        Log.d(TAG, "Notification text: $text")

        // Build and show notification
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_action_name)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
        Log.d(TAG, "Notification sent with ID: $NOTIFICATION_ID")
        
        // Verify notification was posted
        val activeNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            notificationManager.activeNotifications
        } else {
            emptyArray()
        }
        Log.d(TAG, "Active notifications count: ${activeNotifications.size}")
    }

    companion object {
        private const val TAG = "DailyTaskReminder"
        private const val CHANNEL_ID = "daily_task_reminder"
        private const val NOTIFICATION_ID = 1001
    }
}

package com.jaydeep.aimwise

import android.app.Application
import android.util.Log
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.jaydeep.aimwise.worker.DailyTaskReminderWorker
import java.util.Calendar
import java.util.concurrent.TimeUnit

class AimwiseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application onCreate")
        
        // Schedule daily reminder at 4:15 PM
        scheduleDailyReminder()
        
        // Uncomment to test notification immediately
        // testNotificationNow()
    }

    private fun scheduleDailyReminder() {
        // Calculate initial delay to 9:00 PM today or tomorrow
        val currentTime = Calendar.getInstance()
        val targetTime = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 21) // 9 PM
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
        }

        // If 9:00 PM has passed today, schedule for tomorrow
        if (currentTime.after(targetTime)) {
            targetTime.add(Calendar.DAY_OF_MONTH, 1)
        }

        val initialDelay = targetTime.timeInMillis - currentTime.timeInMillis
        Log.d(TAG, "Scheduling daily reminder with initial delay: ${initialDelay / 1000 / 60} minutes")

        // Create constraints
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        // Create periodic work request (runs daily)
        val dailyWorkRequest = PeriodicWorkRequestBuilder<DailyTaskReminderWorker>(
            24, TimeUnit.HOURS,
            15, TimeUnit.MINUTES // Flex interval
        )
            .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
            .setConstraints(constraints)
            .build()

        // Enqueue the work - use REPLACE to update schedule
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DailyTaskReminder",
            ExistingPeriodicWorkPolicy.REPLACE,
            dailyWorkRequest
        )
        
        Log.d(TAG, "Daily reminder scheduled for 9:00 PM")
    }
    
    private fun testNotificationNow() {
        Log.d(TAG, "Scheduling test notification to run in 5 seconds")
        
        val testWorkRequest = OneTimeWorkRequestBuilder<DailyTaskReminderWorker>()
            .setInitialDelay(5, TimeUnit.SECONDS)
            .build()
        
        WorkManager.getInstance(this).enqueue(testWorkRequest)
    }

    companion object {
        private const val TAG = "AimwiseApp"
    }
}

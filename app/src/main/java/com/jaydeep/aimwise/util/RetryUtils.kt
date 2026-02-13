package com.jaydeep.aimwise.util

import com.jaydeep.aimwise.data.model.Result
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlin.math.pow

/**
 * Utility object for implementing retry logic with exponential backoff.
 * Used to handle transient failures in network and database operations.
 */
object RetryUtils {
    
    /**
     * Configuration for retry behavior
     */
    data class RetryConfig(
        val maxAttempts: Int = 3,
        val initialDelayMs: Long = 1000,
        val maxDelayMs: Long = 10000,
        val factor: Double = 2.0,
        val retryableExceptions: List<Class<out Exception>> = listOf(
            java.io.IOException::class.java,
            com.google.firebase.firestore.FirebaseFirestoreException::class.java
        )
    )
    
    /**
     * Executes a suspend function with exponential backoff retry logic.
     * 
     * @param config Retry configuration parameters
     * @param block The suspend function to execute with retry
     * @return Result<T> containing either success data or error
     */
    suspend fun <T> withRetry(
        config: RetryConfig = RetryConfig(),
        block: suspend () -> T
    ): Result<T> {
        var currentAttempt = 0
        var lastException: Exception? = null
        
        while (currentAttempt < config.maxAttempts) {
            try {
                val result = block()
                return Result.Success(result)
            } catch (e: CancellationException) {
                // Re-throw CancellationException to properly cancel coroutines
                throw e
            } catch (e: Exception) {
                lastException = e
                currentAttempt++
                
                // Check if exception is retryable
                val isRetryable = config.retryableExceptions.any { it.isInstance(e) }
                
                if (!isRetryable || currentAttempt >= config.maxAttempts) {
                    break
                }
                
                // Calculate delay with exponential backoff
                val delay = calculateDelay(
                    attempt = currentAttempt,
                    initialDelay = config.initialDelayMs,
                    maxDelay = config.maxDelayMs,
                    factor = config.factor
                )
                
                delay(delay)
            }
        }
        
        return Result.Error(
            Exception(
                "Operation failed after $currentAttempt attempts: ${lastException?.message}",
                lastException
            )
        )
    }
    
    /**
     * Calculates the delay for the next retry attempt using exponential backoff.
     * 
     * @param attempt Current attempt number (1-indexed)
     * @param initialDelay Initial delay in milliseconds
     * @param maxDelay Maximum delay in milliseconds
     * @param factor Exponential factor (typically 2.0)
     * @return Delay in milliseconds for the next attempt
     */
    private fun calculateDelay(
        attempt: Int,
        initialDelay: Long,
        maxDelay: Long,
        factor: Double
    ): Long {
        val exponentialDelay = (initialDelay * factor.pow(attempt - 1)).toLong()
        return minOf(exponentialDelay, maxDelay)
    }
}

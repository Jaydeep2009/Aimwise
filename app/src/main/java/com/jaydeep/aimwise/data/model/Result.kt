package com.jaydeep.aimwise.data.model

/**
 * A sealed class representing the result of an operation.
 * Used for consistent error handling across the application.
 *
 * @param T The type of data contained in a successful result
 */
sealed class Result<out T> {
    /**
     * Represents a successful operation with data
     */
    data class Success<T>(val data: T) : Result<T>()
    
    /**
     * Represents a failed operation with an exception
     */
    data class Error(val exception: Exception) : Result<Nothing>()
    
    /**
     * Represents an operation in progress
     */
    object Loading : Result<Nothing>()
}

package com.jaydeep.aimwise.util

import android.util.Patterns

/**
 * Utility object for input validation
 */
object ValidationUtils {
    
    /**
     * Validates if the given string is a valid email address
     * Uses Android's Patterns.EMAIL_ADDRESS for RFC-compliant validation
     * 
     * @param email The email string to validate
     * @return true if the email is valid, false otherwise
     */
    fun isValidEmail(email: String): Boolean {
        return email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email).matches()
    }
}

package com.jaydeep.aimwise.data.repository


import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.jaydeep.aimwise.data.model.Result
import com.jaydeep.aimwise.util.RetryUtils
import kotlinx.coroutines.tasks.await


/**
 * Repository for managing user authentication with Firebase Auth and Firestore.
 * 
 * Provides both coroutine-based and callback-based authentication methods.
 * The coroutine-based methods (returning Result<T>) are preferred for new code.
 */
class AuthRepository {
    private val auth= FirebaseAuth.getInstance()
    private val firestore= FirebaseFirestore.getInstance()

    fun curentUser()=auth.currentUser
    fun isUserLoggedIn() : Boolean=auth.currentUser!=null

    /**
     * Authenticates a user with email and password.
     * 
     * @param email The user's email address
     * @param password The user's password
     * @return Result.Success if login succeeds, Result.Error with exception details if it fails
     */
    suspend fun login(email: String, password: String): Result<Unit> {
        return try {
            auth.signInWithEmailAndPassword(email, password).await()
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Login failed: ${e.message}"))
        }
    }

    /**
     * Creates a new user account with email and password, then stores user profile in Firestore.
     * 
     * This operation performs two steps:
     * 1. Creates Firebase Auth user with email/password
     * 2. Creates a user document in Firestore with username and email
     * 
     * If the Firestore write fails, the Firebase Auth user is automatically deleted
     * to maintain data consistency. Implements retry logic for transient failures.
     * 
     * @param username The user's display name
     * @param email The user's email address
     * @param password The user's password
     * @return Result.Success if signup succeeds, Result.Error with exception details if it fails
     * @throws Exception if user creation succeeds but user ID is null (should never happen)
     */
    suspend fun signup(username: String, email: String, password: String): Result<Unit> {
        return RetryUtils.withRetry(
            config = RetryUtils.RetryConfig(
                maxAttempts = 3,
                retryableExceptions = listOf(
                    java.io.IOException::class.java,
                    com.google.firebase.firestore.FirebaseFirestoreException::class.java
                )
            )
        ) {
            // Create user with email and password
            auth.createUserWithEmailAndPassword(email, password).await()
            
            val userId = auth.currentUser?.uid 
                ?: throw Exception("User creation succeeded but user ID is null")
            
            // Create user document in Firestore
            val userMap = hashMapOf(
                "username" to username,
                "email" to email
            )
            
            try {
                firestore.collection("users")
                    .document(userId)
                    .set(userMap)
                    .await()
            } catch (firestoreException: Exception) {
                // Firestore write failed, clean up the auth user
                try {
                    auth.currentUser?.delete()?.await()
                } catch (deleteException: Exception) {
                    // Log the delete failure but throw the original Firestore error
                    throw Exception(
                        "Firestore write failed: ${firestoreException.message}. " +
                        "Failed to delete auth user: ${deleteException.message}",
                        firestoreException
                    )
                }
                throw Exception("Firestore write failed: ${firestoreException.message}", firestoreException)
            }
        }
    }

    fun logout(){
        auth.signOut()
    }

    fun getCurrentUserId():String?{
        return auth.currentUser?.uid
    }
    
    /**
     * Gets a fresh Firebase ID token with forced refresh.
     * 
     * This ensures the token is valid and not expired, which is critical for backend API calls.
     * The token is automatically refreshed if it's expired or about to expire.
     * 
     * @param forceRefresh If true, forces Firebase to fetch a new token even if cached one is valid
     * @return The fresh ID token string, or null if user is not authenticated
     */
    suspend fun getFreshToken(forceRefresh: Boolean = true): String? {
        return try {
            auth.currentUser
                ?.getIdToken(forceRefresh)  // Force refresh to avoid expired tokens
                ?.await()
                ?.token
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Fetches the username from Firestore for the current user.
     * 
     * @return The username string, or null if user is not authenticated or username not found
     */
    suspend fun getUsername(): String? {
        return try {
            val userId = auth.currentUser?.uid ?: return null
            val document = firestore.collection("users")
                .document(userId)
                .get()
                .await()
            document.getString("username")
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Signs in with Google using Credential Manager API.
     * 
     * @param context The application context
     * @return Result.Success if login succeeds, Result.Error with exception details if it fails
     */
    suspend fun signInWithGoogle(context: Context): Result<Unit> {
        return try {
            val credentialManager = CredentialManager.create(context)
            
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("224305430342-6onqtdofe5u8e4oqcao288r50k1932pe.apps.googleusercontent.com") // Replace with your Web Client ID from Firebase Console
                .build()
            
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()
            
            val result = credentialManager.getCredential(
                request = request,
                context = context
            )
            
            val credential = result.credential
            val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
            val googleIdToken = googleIdTokenCredential.idToken
            
            val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)
            auth.signInWithCredential(firebaseCredential).await()
            
            // Check if user document exists in Firestore, create if not
            val userId = auth.currentUser?.uid ?: throw Exception("User ID is null after Google sign-in")
            val userDoc = firestore.collection("users").document(userId).get().await()
            
            if (!userDoc.exists()) {
                // Create user document with Google account info
                val userMap = hashMapOf(
                    "username" to (auth.currentUser?.displayName ?: "User"),
                    "email" to (auth.currentUser?.email ?: "")
                )
                firestore.collection("users").document(userId).set(userMap).await()
            }
            
            Result.Success(Unit)
        } catch (e: Exception) {
            Result.Error(Exception("Google sign-in failed: ${e.message}"))
        }
    }
    
    // Legacy callback-based methods for backward compatibility
    // TODO: Remove these once AuthViewModel is refactored to use coroutines (Task 9)
    fun login(email: String, password: String, callback: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    callback(true, "Login successful")
                } else {
                    callback(false, task.exception?.message ?: "Login failed")
                }
            }
    }
    
    fun signup(username: String, email: String, password: String, callback: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val userId = auth.currentUser?.uid
                    if (userId != null) {
                        val userMap = hashMapOf(
                            "username" to username,
                            "email" to email
                        )
                        firestore.collection("users")
                            .document(userId)
                            .set(userMap)
                            .addOnSuccessListener {
                                callback(true, "Signup successful")
                            }
                            .addOnFailureListener { e ->
                                // Clean up auth user if Firestore write fails
                                auth.currentUser?.delete()
                                callback(false, "Failed to create user profile: ${e.message}")
                            }
                    } else {
                        callback(false, "User creation succeeded but user ID is null")
                    }
                } else {
                    callback(false, task.exception?.message ?: "Signup failed")
                }
            }
    }
}
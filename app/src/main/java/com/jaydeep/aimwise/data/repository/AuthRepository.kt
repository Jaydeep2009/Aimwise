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
 */
class AuthRepository {
    private val auth= FirebaseAuth.getInstance()
    private val firestore= FirebaseFirestore.getInstance()

    fun curentUser()=auth.currentUser
    fun isUserLoggedIn() : Boolean=auth.currentUser!=null

    /**
     * Authenticates user with email and password.
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
     * Creates user account and stores profile in Firestore.
     * If Firestore write fails, auth user is automatically deleted for consistency.
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
     * Gets a fresh Firebase ID token for backend API calls.
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
     * Fetches username from Firestore for current user.
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
     * Note: May fail with SecurityException due to SHA-1 fingerprint mismatch or Play Services issues.
     */
    suspend fun signInWithGoogle(context: Context): Result<Unit> {
        return try {
            val credentialManager = CredentialManager.create(context)
            
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId("224305430342-6onqtdofe5u8e4oqcao288r50k1932pe.apps.googleusercontent.com")
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
        } catch (e: SecurityException) {
            android.util.Log.e("AuthRepository", "Google Sign-In SecurityException: ${e.message}")
            Result.Error(Exception("Google sign-in failed: SHA-1 fingerprint mismatch or Play Services issue. Check logs for details."))
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Google Sign-In failed: ${e.message}", e)
            Result.Error(Exception("Google sign-in failed: ${e.message}"))
        }
    }
}
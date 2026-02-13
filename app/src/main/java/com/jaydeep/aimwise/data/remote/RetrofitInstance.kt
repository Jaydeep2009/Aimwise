package com.jaydeep.aimwise.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.gson.GsonBuilder
import com.jaydeep.aimwise.BuildConfig
import com.jaydeep.aimwise.data.model.DayPlan
import com.jaydeep.aimwise.data.model.DayPlanDeserializer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitInstance {

    private const val BASE_URL = "https://aimwise.onrender.com/"

    /**
     * Interceptor that automatically attaches Firebase authentication token to all API requests.
     * Forces token refresh to ensure we always send a valid, non-expired token.
     */
    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        
        // Get current user and token with detailed logging
        val user = FirebaseAuth.getInstance().currentUser
        val token = runBlocking {
            try {
                user?.getIdToken(true)  // 🔥 CRITICAL: true forces token refresh
                    ?.await()
                    ?.token
            } catch (e: Exception) {
                android.util.Log.e("AUTH_INTERCEPTOR", "Token fetch failed: ${e.message}")
                null
            }
        }
        
        // Debug logging
        android.util.Log.d("AUTH_CHECK", "User UID: ${user?.uid ?: "NULL"}")
        android.util.Log.d("AUTH_CHECK", "Token: ${token?.take(20) ?: "NULL"}...")
        android.util.Log.d("AUTH_CHECK", "Request URL: ${originalRequest.url}")
        
        // Add Authorization header if token exists
        val newRequest = if (token != null) {
            originalRequest.newBuilder()
                .addHeader("Authorization", "Bearer $token")
                .build()
        } else {
            android.util.Log.e("AUTH_CHECK", "⚠️ No token available - request will fail!")
            originalRequest
        }
        
        chain.proceed(newRequest)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)  // 🔥 Add auth interceptor FIRST
        .apply {
            // Only add HTTP logging in debug builds
            if (BuildConfig.DEBUG) {
                val logger = HttpLoggingInterceptor().apply {
                    level = HttpLoggingInterceptor.Level.BODY
                }
                addInterceptor(logger)
            }
        }
        .build()

    private val gson = GsonBuilder()
        .registerTypeAdapter(DayPlan::class.java, DayPlanDeserializer())
        .create()

    val api: ApiService = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .build()
        .create(ApiService::class.java)

}

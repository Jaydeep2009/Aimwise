package com.jaydeep.aimwise.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.jaydeep.aimwise.ui.screens.HomeScreen
import com.jaydeep.aimwise.ui.screens.auth.Login
import com.jaydeep.aimwise.ui.screens.auth.Signup
import com.jaydeep.aimwise.ui.screens.skip.SkipActionScreen
import com.jaydeep.aimwise.ui.screens.roadmap.FullRoadmapScreen
import com.jaydeep.aimwise.ui.screens.roadmap.RoadmapScreen
import com.jaydeep.aimwise.viewmodel.GoalViewModel

@Composable
fun AppNav(goalViewModel: GoalViewModel){
    val navController = rememberNavController()
    val auth = FirebaseAuth.getInstance()
    
    // Observe authentication state changes
    var isLoggedIn by remember { mutableStateOf(auth.currentUser != null) }
    
    // Listen for auth state changes
    DisposableEffect(Unit) {
        val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val wasLoggedIn = isLoggedIn
            val newUser = firebaseAuth.currentUser
            isLoggedIn = newUser != null
            
            android.util.Log.d("APP_NAV", "Auth state changed - Was: $wasLoggedIn, Now: $isLoggedIn, User: ${newUser?.uid}")
            
            // Navigate to login if user logged out
            if (wasLoggedIn && !isLoggedIn) {
                android.util.Log.d("APP_NAV", "User logged out - navigating to login")
                navController.navigate("login") {
                    popUpTo(0) { inclusive = true }
                }
            }
            // Navigate to home if user logged in
            else if (!wasLoggedIn && isLoggedIn) {
                android.util.Log.d("APP_NAV", "User logged in - navigating to home")
                navController.navigate("home") {
                    popUpTo(0) { inclusive = true }
                }
            }
        }
        auth.addAuthStateListener(authStateListener)
        
        onDispose {
            auth.removeAuthStateListener(authStateListener)
        }
    }

    NavHost(
        navController = navController,
        startDestination = if(isLoggedIn) "home" else "login"
    ) {
        composable("login") {
            Login(navController)
        }
        composable("signup") {
            Signup(navController)
        }
        composable("home") {
            HomeScreen(navController,goalViewModel)
        }

        composable(
            "skip/{goalId}/{day}"
        ) {
            val goalId = it.arguments?.getString("goalId") ?: ""
            val day = it.arguments?.getString("day")?.toInt() ?: 0

            SkipActionScreen(goalId, day, navController)
        }

        composable("roadmap/{goalId}") { backStack ->
            val goalId = backStack.arguments?.getString("goalId")!!
            RoadmapScreen(goalId, navController,goalViewModel)
        }

        composable("fullRoadmap/{goalId}") { backStack ->
            val goalId = backStack.arguments?.getString("goalId")!!
            FullRoadmapScreen(goalId, navController, goalViewModel)
        }
    }
}
package com.jaydeep.aimwise.ui.navigation

import RoadmapScreen
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.firebase.auth.FirebaseAuth
import com.jaydeep.aimwise.ui.screens.LoadingScreen
import com.jaydeep.aimwise.ui.screens.HomeScreen
import com.jaydeep.aimwise.ui.screens.auth.Login
import com.jaydeep.aimwise.ui.screens.auth.Signup
import com.jaydeep.aimwise.ui.screens.skip.SkipActionScreen

@Composable
fun AppNav(){
    val navController = rememberNavController()
    var auth = FirebaseAuth.getInstance()

    val currentUser= auth.currentUser

    NavHost(
        navController = navController,
        startDestination = if(currentUser!=null) "home" else "login"
    ) {
        composable("login") {
            Login(navController)
        }
        composable("signup") {
            Signup(navController)
        }
        composable("home") {
            HomeScreen(navController)
        }
        composable("loading/{goal}/{days}") { backStack ->
            val goal = Uri.decode(backStack.arguments?.getString("goal") ?: "")
            val days = backStack.arguments?.getString("days")?.toInt() ?: 30

            LoadingScreen(navController, goal, days)
        }


        composable("loading/{goal}/{days}") { backStack ->
            val goal = backStack.arguments?.getString("goal") ?: ""
            val days = backStack.arguments?.getString("days")?.toInt() ?: 30
            LoadingScreen(navController, goal, days)
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
            RoadmapScreen(goalId, navController)
        }




    }
}
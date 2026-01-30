package com.jaydeep.aimwise.ui.navigation

import RoadmapScreen
import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.auth.FirebaseAuth
import com.jaydeep.aimwise.ui.screens.auth.HomeScreen
import com.jaydeep.aimwise.ui.screens.auth.Login
import com.jaydeep.aimwise.ui.screens.auth.Signup

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
        composable(
            route = "roadmap/{goal}",
            arguments = listOf(
                navArgument("goal") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val goal = backStackEntry.arguments?.getString("goal") ?: ""
            RoadmapScreen(goal)
        }

    }
}
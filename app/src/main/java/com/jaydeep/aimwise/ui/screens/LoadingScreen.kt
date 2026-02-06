package com.jaydeep.aimwise.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jaydeep.aimwise.viewmodel.GoalViewModel

@Composable
fun LoadingScreen(
    navController: NavHostController,
    goal: String,
    days: Int,
    goalViewModel: GoalViewModel = viewModel()
) {

    LaunchedEffect(Unit) {
        goalViewModel.generateGoalWithRoadmap(goal, days)
        navController.navigate("home") {
            popUpTo("home") { inclusive = true }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Generating roadmap...")
            Spacer(modifier = Modifier.height(20.dp))
            androidx.compose.material3.CircularProgressIndicator()
        }
    }
}

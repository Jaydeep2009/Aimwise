package com.jaydeep.aimwise.ui.screens

import androidx.compose.runtime.Composable

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

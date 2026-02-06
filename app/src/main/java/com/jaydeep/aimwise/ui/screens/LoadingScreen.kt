package com.jaydeep.aimwise.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
    viewModel: GoalViewModel = viewModel()
) {
    val done by viewModel.done.collectAsState()

    // run only once
    LaunchedEffect(true) {
        viewModel.generateGoalWithRoadmap(goal, days)
    }

    // navigate only when done
    LaunchedEffect(done) {
        if (done) {
            navController.navigate("home") {
                popUpTo("home") { inclusive = true }
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = "Generating your roadmap...")
    }
}

package com.jaydeep.aimwise.ui.screens.roadmap

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jaydeep.aimwise.ui.components.FullScreenError
import com.jaydeep.aimwise.ui.components.FullScreenLoading
import com.jaydeep.aimwise.ui.screens.skip.SkipActionScreen
import com.jaydeep.aimwise.ui.state.ViewState
import com.jaydeep.aimwise.viewmodel.GoalViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapScreen(
    goalId: String,
    navController: NavHostController,
    viewModel: GoalViewModel,
) {
    LaunchedEffect(goalId) {
        viewModel.loadRoadmap(goalId)
        viewModel.checkMissedDay(goalId)
    }

    val goalState by viewModel.goalState.collectAsState()
    val dayPlanState by viewModel.dayPlanState.collectAsState()
    val pending by viewModel.pendingAdjustment.collectAsState()
    val missedDay by viewModel.missedDay.collectAsState()
    val day by viewModel.currentDay.collectAsState()

    // Get goal title for TopAppBar
    val goalTitle by remember {
        derivedStateOf {
            (goalState as? ViewState.Success)?.data?.title ?: "Roadmap"
        }
    }

    // 🚨 skip block
    if (pending && missedDay != null) {
        SkipActionScreen(goalId, missedDay!!, navController)
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(goalTitle) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                goalState is ViewState.Loading || dayPlanState is ViewState.Loading -> {
                    FullScreenLoading(message = "Loading roadmap...")
                }
                goalState is ViewState.Error -> {
                    FullScreenError(
                        message = (goalState as ViewState.Error).message,
                        onRetry = { viewModel.loadRoadmap(goalId) }
                    )
                }
                dayPlanState is ViewState.Error -> {
                    FullScreenError(
                        message = (dayPlanState as ViewState.Error).message,
                        onRetry = { viewModel.loadRoadmap(goalId) }
                    )
                }
                goalState is ViewState.Success && dayPlanState is ViewState.Success -> {
            val goal = (goalState as ViewState.Success).data
            val dayPlan = (dayPlanState as ViewState.Success).data

            // Use remember to avoid recalculating on every recomposition
            val currentDayValue = remember(day) { day ?: 1 }
            
            // Calculate task completion percentage
            val completedTasks = remember(dayPlan) { dayPlan.tasks.count { it.isCompleted } }
            val totalTasks = remember(dayPlan) { dayPlan.tasks.size }
            val taskProgress by remember(completedTasks, totalTasks) {
                derivedStateOf { 
                    if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 0f
                }
            }
            
            val dayText by remember(currentDayValue, goal.durationDays) {
                derivedStateOf { "Day $currentDayValue of ${goal.durationDays}" }
            }
            
            val progressText by remember(completedTasks, totalTasks) {
                derivedStateOf { "$completedTasks of $totalTasks tasks completed" }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {

                Text(goal.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(8.dp))

                Text(dayText)

                Spacer(modifier = Modifier.height(4.dp))

                Text(progressText, fontSize = 14.sp)

                Spacer(modifier = Modifier.height(12.dp))

                LinearProgressIndicator(
                    progress = { taskProgress },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                // View Full Roadmap Button
                Button(
                    onClick = { navController.navigate("fullRoadmap/$goalId") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("📋 View Full Roadmap")
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Today's Tasks",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                val tasks = dayPlan.tasks

                tasks.forEachIndexed { index, task ->
                    // Use index as key for efficient recomposition
                    androidx.compose.runtime.key(index) {
                        TaskRow(
                            task = task,
                            onToggle = { viewModel.toggleTask(goalId, index) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Note: Days automatically progress at midnight",
                    fontSize = 12.sp,
                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
            }
        }
    }
}


/**
 * Extracted TaskRow composable to minimize recompositions.
 * Only recomposes when the task itself changes, not when other tasks change.
 */
@Composable
private fun TaskRow(
    task: com.jaydeep.aimwise.data.model.Task,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ){
        Checkbox(
            checked = task.isCompleted,
            onCheckedChange = { onToggle() }
        )
        Text(task.description)
    }
}

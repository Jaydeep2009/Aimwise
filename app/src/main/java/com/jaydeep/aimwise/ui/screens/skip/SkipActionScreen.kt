package com.jaydeep.aimwise.ui.screens.skip

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jaydeep.aimwise.viewmodel.GoalViewModel
import kotlinx.coroutines.launch

@Composable
fun SkipActionScreen(
    goalId: String,
    missedDay: Int,
    navController: NavHostController?,
    viewModel: GoalViewModel
) {
    android.util.Log.d("SkipActionScreen", "=== SkipActionScreen displayed ===")
    android.util.Log.d("SkipActionScreen", "Goal ID: $goalId, Missed Day: $missedDay")

    var loading by remember { mutableStateOf(false) }
    var actionStartTime by remember { mutableStateOf(0L) }
    var incompleteTasks by remember { mutableStateOf<List<String>>(emptyList()) }
    val scope = rememberCoroutineScope()
    
    // Load incomplete tasks
    LaunchedEffect(goalId, missedDay) {
        try {
            incompleteTasks = viewModel.getIncompleteTasks(goalId, missedDay)
            android.util.Log.d("SkipActionScreen", "Loaded ${incompleteTasks.size} incomplete tasks")
        } catch (e: Exception) {
            android.util.Log.e("SkipActionScreen", "Error loading incomplete tasks: ${e.message}")
        }
    }
    
    // Observe pending adjustment state
    val pendingAdjustment by viewModel.pendingAdjustment.collectAsState()
    
    // Navigate back when pending adjustment is cleared OR after timeout
    LaunchedEffect(pendingAdjustment, loading) {
        if (loading) {
            val elapsed = System.currentTimeMillis() - actionStartTime
            android.util.Log.d("SkipActionScreen", "Loading state - pendingAdjustment: $pendingAdjustment, elapsed: ${elapsed}ms")
            
            if (!pendingAdjustment) {
                android.util.Log.d("SkipActionScreen", "Pending adjustment cleared, navigating back")
                kotlinx.coroutines.delay(500)
                navController?.popBackStack()
            } else if (elapsed > 10000) {
                // Timeout after 10 seconds (increased from 5)
                android.util.Log.e("SkipActionScreen", "Timeout waiting for action to complete, forcing navigation")
                navController?.popBackStack()
            }
        }
    }

    // 🚫 block back press
    BackHandler(enabled = true) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .padding(20.dp)
    ) {

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(40.dp))

            Text(
                text = "You missed day $missedDay",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "How should we adjust your roadmap?",
                color = Color.Gray
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Display incomplete tasks if any
            if (incompleteTasks.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF1A1D24)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "Incomplete Tasks:",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        incompleteTasks.forEachIndexed { index, task ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Text(
                                    text = "${index + 1}. ",
                                    color = Color(0xFF6C63FF),
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = task,
                                    color = Color.Gray,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 🔥 OPTION A: SKIP
            OptionCard(
                title = "Skip",
                desc = "Leave roadmap unchanged, continue from next day",
                onClick = {
                    android.util.Log.d("SkipActionScreen", "User selected: SKIP")
                    loading = true
                    actionStartTime = System.currentTimeMillis()
                    viewModel.resolveSkip(goalId, "SKIP")
                }
            )

            // 🔥 OPTION B: MARK_COMPLETED
            OptionCard(
                title = "Mark as Completed",
                desc = "Mark day as skipped and redistribute incomplete tasks to remaining days",
                onClick = {
                    android.util.Log.d("SkipActionScreen", "User selected: MARK_COMPLETED")
                    loading = true
                    actionStartTime = System.currentTimeMillis()
                    viewModel.resolveSkip(goalId, "MARK_COMPLETED")
                }
            )

            // 🔥 OPTION C: ADJUST_ROADMAP
            OptionCard(
                title = "Adjust Roadmap",
                desc = "Redistribute incomplete tasks across remaining days",
                onClick = {
                    android.util.Log.d("SkipActionScreen", "User selected: ADJUST_ROADMAP")
                    loading = true
                    actionStartTime = System.currentTimeMillis()
                    viewModel.resolveSkip(goalId, "ADJUST_ROADMAP")
                }
            )
        }

        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = Color.White
            )
        }
    }
}

@Composable
fun OptionCard(
    title: String,
    desc: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF1A1D24)
        )
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(desc, color = Color.Gray)
        }
    }
}

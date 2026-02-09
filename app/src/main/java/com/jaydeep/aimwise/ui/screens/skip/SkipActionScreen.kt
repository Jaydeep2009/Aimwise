package com.jaydeep.aimwise.ui.screens.skip

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
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

@Composable
fun SkipActionScreen(
    goalId: String,
    missedDay: Int,
    navController: NavHostController?,
    viewModel: GoalViewModel = viewModel()
) {

    var loading by remember { mutableStateOf(false) }

    // 🚫 block back press
    BackHandler(enabled = true) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115))
            .padding(20.dp)
    ) {

        Column(
            modifier = Modifier.fillMaxSize(),
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

            Spacer(modifier = Modifier.height(40.dp))

            // 🔥 OPTION 1
            OptionCard(
                title = "Adjust automatically",
                desc = "We redistribute tasks smartly",
                onClick = {
                    loading = true
                    viewModel.resolveSkip(goalId, "AUTO")
                    navController?.popBackStack()
                }
            )

            // 🔥 OPTION 2
            OptionCard(
                title = "Extend duration",
                desc = "Add extra days to finish comfortably",
                onClick = {
                    loading = true
                    viewModel.resolveSkip(goalId, "EXTEND")
                    navController?.popBackStack()
                }
            )

            // 🔥 OPTION 3
            OptionCard(
                title = "Keep duration",
                desc = "Increase tasks per day",
                onClick = {
                    loading = true
                    viewModel.resolveSkip(goalId, "INCREASE")
                    navController?.popBackStack()
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

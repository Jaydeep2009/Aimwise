package com.jaydeep.aimwise.ui.screens.auth

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel

import androidx.navigation.NavHostController
import com.jaydeep.aimwise.data.model.Goal
import com.jaydeep.aimwise.viewmodel.GoalViewModel


@Composable
fun HomeScreen(navController: NavHostController) {
    val goalViewModel : GoalViewModel= viewModel()
    val goals by goalViewModel.goals.collectAsState()
    var showDialog by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        goalViewModel.loadGoals()
    }


    Box(modifier = Modifier.fillMaxSize()) {

        // Goals List
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            items(goals) { goal ->
                GoalCard(goal){
                    navController.navigate("roadmap/${goal.id}")

                }
            }
        }

        // Floating Add Button
        FloatingActionButton(
            onClick = { showDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
        ) {
            Text("+",
            fontSize = 30.sp)
        }
    }

    // Add Goal Dialog
    if (showDialog) {
        AddGoalDialog(
            onAdd = { newGoal ->
                goalViewModel.addGoal(newGoal)
                showDialog = false
            },
            onDismiss = { showDialog = false }
        )
    }
}


@Composable
fun GoalCard(goal: Goal,
             onClick: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .clickable{onClick()},
        elevation = CardDefaults.cardElevation(6.dp)
    ) {
        Text(
            text = goal.title,
            modifier = Modifier.padding(16.dp),
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun AddGoalDialog(
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var goalText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                Text(
                    text = "Add Your Goal",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = goalText,
                    onValueChange = { goalText = it },
                    label = { Text("Goal") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (goalText.isNotBlank()) {
                            onAdd(goalText)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Add Goal")
                }
            }
        }
    }
}




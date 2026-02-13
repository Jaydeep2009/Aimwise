package com.jaydeep.aimwise.ui.screens

import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavHostController
import com.google.firebase.auth.FirebaseAuth
import com.jaydeep.aimwise.data.model.Goal
import com.jaydeep.aimwise.data.repository.AuthRepository
import com.jaydeep.aimwise.ui.components.FullScreenError
import com.jaydeep.aimwise.ui.components.FullScreenLoading
import com.jaydeep.aimwise.ui.state.ViewState
import com.jaydeep.aimwise.viewmodel.GoalViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Calendar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavHostController, goalViewModel: GoalViewModel) {

    val goalsState by goalViewModel.goalsState.collectAsState()
    val roadmapState by goalViewModel.roadmapGenerationState.collectAsState()

    var showDialog by remember { mutableStateOf(false) }
    var showLoadingDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val authRepository = remember { AuthRepository() }
    val lifecycleOwner = LocalLifecycleOwner.current

    // Fetch username from Firestore
    LaunchedEffect(FirebaseAuth.getInstance().currentUser?.uid) {
        username = authRepository.getUsername()
    }

    // Reload goals when user changes
    LaunchedEffect(FirebaseAuth.getInstance().currentUser?.uid) {
        val user = FirebaseAuth.getInstance().currentUser
        Log.d("HOME_SCREEN", "User changed: ${user?.uid}")

        if (user != null) {
            delay(300)
            goalViewModel.loadGoals()
        }
    }
    
    // Reload goals when app resumes (to update day after midnight)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                Log.d("HOME_SCREEN", "App resumed, reloading goals to update day")
                goalViewModel.loadGoals()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // After roadmap generation
    LaunchedEffect(roadmapState) {
        if (roadmapState is ViewState.Success && showLoadingDialog) {
            showLoadingDialog = false
            goalViewModel.resetDoneState()
            goalViewModel.loadGoals()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Aimwise") },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Default.Menu,
                                contentDescription = "Menu"
                            )
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            // Username display
                            val displayName = username ?: "User"

                            DropdownMenuItem(
                                text = { 
                                    Text(
                                        text = displayName,
                                        fontWeight = FontWeight.Bold
                                    ) 
                                },
                                onClick = { },
                                enabled = false
                            )
                            
                            HorizontalDivider()
                            
                            DropdownMenuItem(
                                text = { Text("Logout") },
                                onClick = {
                                    showMenu = false
                                    FirebaseAuth.getInstance().signOut()
                                    scope.launch {
                                        delay(200)
                                        navController.navigate("login") {
                                            popUpTo(navController.graph.startDestinationId) {
                                                inclusive = true
                                            }
                                        }
                                    }
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.ExitToApp,
                                        contentDescription = null
                                    )
                                }
                            )
                        }
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showDialog = true }) {
                Text("+", fontSize = 30.sp)
            }
        }
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {

            when (val state = goalsState) {

                is ViewState.Loading -> {
                    FullScreenLoading("Loading goals...")
                }

                is ViewState.Success -> {
                    val goals = state.data

                    if (goals.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text("No goals yet", fontSize = 24.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Create your first goal")
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            items(goals, key = { it.id }) { goal ->
                                GoalCard(
                                    goal = goal,
                                    goalViewModel = goalViewModel,
                                    onClick = {
                                        navController.navigate("roadmap/${goal.id}")
                                    }
                                )
                            }
                        }
                    }
                }

                is ViewState.Error -> {

                    val isAuthError = state.message.contains("authenticated", true)

                    if (isAuthError) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                "Authentication Error",
                                fontSize = 24.sp,
                                color = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(state.message, textAlign = TextAlign.Center)
                            Spacer(modifier = Modifier.height(24.dp))

                            Button(onClick = {
                                FirebaseAuth.getInstance().signOut()
                                scope.launch {
                                    delay(300)
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            inclusive = true
                                        }
                                    }
                                }
                            }) {
                                Text("Logout and Login Again")
                            }
                        }
                    } else {
                        FullScreenError(
                            message = state.message,
                            onRetry = { goalViewModel.loadGoals() }
                        )
                    }
                }
            }
        }
    }

    if (showDialog) {
        AddGoalDialog(
            onAdd = { goal, days ->
                showDialog = false
                showLoadingDialog = true
                goalViewModel.generateGoalWithRoadmap(goal, days)
            },
            onDismiss = { showDialog = false }
        )
    }

    if (showLoadingDialog) {
        RoadmapGenerationDialog(
            roadmapState = roadmapState,
            onDismiss = {
                showLoadingDialog = false
                goalViewModel.resetDoneState()
            }
        )
    }
}

@Composable
fun GoalCard(
    goal: Goal,
    goalViewModel: GoalViewModel,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable { onClick() }
    ) {

        Box(modifier = Modifier.fillMaxWidth()) {

            Column(modifier = Modifier.padding(16.dp)) {
                Text(goal.title, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Day ${goal.currentDay} of ${goal.durationDays}")
            }

            IconButton(
                onClick = { goalViewModel.deleteGoal(goal.id) },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
fun AddGoalDialog(
    onAdd: (String, Int) -> Unit,
    onDismiss: () -> Unit
) {
    var goalText by remember { mutableStateOf("") }
    var days by remember { mutableStateOf(30f) }
    var showError by remember { mutableStateOf(false) }

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
                    onValueChange = { 
                        goalText = it
                        showError = false
                    },
                    label = { Text("Goal") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Please enter a goal", color = MaterialTheme.colorScheme.error) }
                    } else null
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Duration: ${days.toInt()} days")

                Slider(
                    value = days,
                    onValueChange = { days = it },
                    valueRange = 1f..90f
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (goalText.isNotBlank()) {
                            onAdd(goalText, days.toInt())
                        } else {
                            showError = true
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Generate Roadmap")
                }
            }
        }
    }
}

@Composable
fun RoadmapGenerationDialog(
    roadmapState: ViewState<Unit>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (roadmapState !is ViewState.Loading) onDismiss() }) {
        Card {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {

                when (roadmapState) {
                    is ViewState.Loading -> {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Generating roadmap...")
                    }

                    is ViewState.Success -> {
                        Text("Success!")
                        Button(onClick = onDismiss) { Text("OK") }
                    }

                    is ViewState.Error -> {
                        Text(roadmapState.message)
                        Button(onClick = onDismiss) { Text("Close") }
                    }
                }
            }
        }
    }
}

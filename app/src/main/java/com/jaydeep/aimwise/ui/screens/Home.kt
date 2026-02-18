package com.jaydeep.aimwise.ui.screens

import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

// ─── Color Palette ────────────────────────────────────────────────────────────
private val BG_PRIMARY       = Color(0xFFF8FAFF)
private val BG_SURFACE       = Color(0xFFFFFFFF)
private val BG_CARD_GRADIENT = Color(0xFFFAFBFF)

private val ACCENT_PRIMARY   = Color(0xFF6366F1)
private val ACCENT_LIGHT     = Color(0xFFEEF2FF)
private val ACCENT_BORDER    = Color(0xFFD4D8FF)

private val SUCCESS_COLOR    = Color(0xFF10B981)
private val SUCCESS_LIGHT    = Color(0xFFECFDF5)
private val SUCCESS_BORDER   = Color(0xFFA7F3D0)

private val WARNING_COLOR    = Color(0xFFF59E0B)
private val WARNING_LIGHT    = Color(0xFFFFF8EB)

private val ERROR_COLOR      = Color(0xFFF43F5E)
private val ERROR_LIGHT      = Color(0xFFFFF1F3)

private val TEXT_PRIMARY     = Color(0xFF1E1B4B)
private val TEXT_SECONDARY   = Color(0xFF475569)
private val TEXT_MUTED       = Color(0xFF94A3B8)
private val DIVIDER_COLOR    = Color(0xFFE2E8F0)

// ─── Main Home Screen ─────────────────────────────────────────────────────────
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

    LaunchedEffect(FirebaseAuth.getInstance().currentUser?.uid) {
        val user = FirebaseAuth.getInstance().currentUser
        Log.d("HOME_SCREEN", "User changed: ${user?.uid}")

        if (user != null) {
            username = authRepository.getUsername()
            delay(300)
            goalViewModel.loadGoals()
        }
    }

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
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(ACCENT_PRIMARY, Color(0xFF8B5CF6))
                                    )
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Star,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Aimwise",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(ACCENT_LIGHT)
                        ) {
                            Icon(
                                Icons.Rounded.Person,
                                contentDescription = "Menu",
                                tint = ACCENT_PRIMARY
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier
                                .width(200.dp)
                                .background(BG_SURFACE, RoundedCornerShape(16.dp))
                        ) {
                            val displayName = username ?: "User"

                            // User header
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(ACCENT_LIGHT)
                                    .padding(16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.AccountCircle,
                                    contentDescription = null,
                                    tint = ACCENT_PRIMARY,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = displayName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = TEXT_PRIMARY
                                )
                            }

                            HorizontalDivider()

                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "Logout",
                                        fontWeight = FontWeight.Medium
                                    )
                                },
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
                                        Icons.Rounded.ExitToApp,
                                        contentDescription = null,
                                        tint = ERROR_COLOR
                                    )
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BG_SURFACE
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showDialog = true },
                containerColor = ACCENT_PRIMARY,
                contentColor = Color.White,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(64.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = "Add goal",
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        containerColor = BG_PRIMARY
    ) { padding ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Decorative background blobs
            Box(
                modifier = Modifier
                    .size(300.dp)
                    .offset(x = (-100).dp, y = (-100).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(ACCENT_LIGHT.copy(alpha = 0.6f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 80.dp, y = 80.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(SUCCESS_LIGHT.copy(alpha = 0.5f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )

            when (val state = goalsState) {

                is ViewState.Loading -> {
                    FullScreenLoading("Loading goals...")
                }

                is ViewState.Success -> {
                    val goals = state.data

                    if (goals.isEmpty()) {
                        EmptyGoalsState(
                            onAddGoal = { showDialog = true }
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            item {
                                WelcomeHeader(username = username ?: "there")
                                Spacer(modifier = Modifier.height(8.dp))
                            }

                            itemsIndexed(
                                items = goals,
                                key = { _, goal -> goal.id }
                            ) { index, goal ->
                                GoalCard(
                                    goal = goal,
                                    goalViewModel = goalViewModel,
                                    animationDelay = index * 50,
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
                        AuthErrorState(
                            message = state.message,
                            onLogout = {
                                FirebaseAuth.getInstance().signOut()
                                scope.launch {
                                    delay(300)
                                    navController.navigate("login") {
                                        popUpTo(navController.graph.startDestinationId) {
                                            inclusive = true
                                        }
                                    }
                                }
                            }
                        )
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
            }, onDismiss = { showDialog = false }
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

// ─── Welcome Header ───────────────────────────────────────────────────────────
@Composable
private fun WelcomeHeader(username: String) {
    Column {
        Text(
            text = "Welcome back, $username!",
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            color = TEXT_PRIMARY,
            lineHeight = 32.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Let's achieve your goals together",
            fontSize = 15.sp,
            color = TEXT_MUTED
        )
    }
}

// ─── Empty Goals State ────────────────────────────────────────────────────────
@Composable
private fun EmptyGoalsState(onAddGoal: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Empty state illustration
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(ACCENT_LIGHT),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Star,
                contentDescription = null,
                tint = ACCENT_PRIMARY,
                modifier = Modifier.size(60.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "No goals yet",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = TEXT_PRIMARY
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Start your journey by creating\nyour first goal",
            fontSize = 15.sp,
            color = TEXT_MUTED,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onAddGoal,
            colors = ButtonDefaults.buttonColors(
                containerColor = ACCENT_PRIMARY
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .height(50.dp)
                .widthIn(min = 180.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Create First Goal",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
        }
    }
}

// ─── Auth Error State ─────────────────────────────────────────────────────────
@Composable
private fun AuthErrorState(message: String, onLogout: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(ERROR_LIGHT),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.Warning,
                contentDescription = null,
                tint = ERROR_COLOR,
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Authentication Error",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = TEXT_PRIMARY
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            message,
            textAlign = TextAlign.Center,
            color = TEXT_SECONDARY,
            fontSize = 14.sp,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(28.dp))

        Button(
            onClick = onLogout,
            colors = ButtonDefaults.buttonColors(
                containerColor = ERROR_COLOR
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.height(50.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.ExitToApp,
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Logout and Login Again", fontWeight = FontWeight.Bold)
        }
    }
}

// ─── Goal Card ────────────────────────────────────────────────────────────────
@Composable
fun GoalCard(
    goal: Goal,
    goalViewModel: GoalViewModel,
    animationDelay: Int = 0,
    onClick: () -> Unit
) {
    val progress = if (goal.totalTasks > 0) {
        goal.completedTasks.toFloat() / goal.totalTasks
    } else 0f

    val hasPendingTasks = goal.todayTotalTasks > 0 &&
            goal.todayCompletedTasks < goal.todayTotalTasks

    var showDeleteDialog by remember { mutableStateOf(false) }

    // Progress-based color
    val progressColor = when {
        progress >= 0.8f -> SUCCESS_COLOR
        progress >= 0.4f -> WARNING_COLOR
        else -> ACCENT_PRIMARY
    }

    val progressBg = when {
        progress >= 0.8f -> SUCCESS_LIGHT
        progress >= 0.4f -> WARNING_LIGHT
        else -> ACCENT_LIGHT
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 8.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = ACCENT_PRIMARY.copy(alpha = 0.08f),
                spotColor = ACCENT_PRIMARY.copy(alpha = 0.12f)
            )
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = BG_SURFACE),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BG_SURFACE, BG_CARD_GRADIENT),
                            start = Offset(0f, 0f),
                            end = Offset(1000f, 600f)
                        )
                    )
                    .padding(18.dp)
                    .padding(end = 36.dp)
            ) {
                // Title row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = goal.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TEXT_PRIMARY,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (hasPendingTasks) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(WARNING_LIGHT)
                                .border(1.dp, WARNING_COLOR.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Notifications,
                                contentDescription = "Pending tasks",
                                tint = WARNING_COLOR,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Day and progress info
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Day badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(progressBg)
                            .border(1.dp, progressColor.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DateRange,
                                contentDescription = null,
                                tint = progressColor,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Day ${goal.currentDay}/${goal.durationDays}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = progressColor
                            )
                        }
                    }

                    // Tasks badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ACCENT_LIGHT)
                            .border(1.dp, ACCENT_BORDER, RoundedCornerShape(10.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = ACCENT_PRIMARY,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "${goal.completedTasks}/${goal.totalTasks}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = ACCENT_PRIMARY
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress bar
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "${(progress * 100).toInt()}% Complete",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TEXT_SECONDARY
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(50))
                            .background(progressBg)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(progress)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            progressColor,
                                            progressColor.copy(alpha = 0.8f)
                                        )
                                    )
                                )
                        )
                    }
                }
            }

            // Delete button
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(ERROR_LIGHT)
                    .clickable { showDeleteDialog = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.Delete,
                    contentDescription = "Delete",
                    tint = ERROR_COLOR,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showDeleteDialog) {
        DeleteConfirmationDialog(
            goalTitle = goal.title,
            onConfirm = {
                goalViewModel.deleteGoal(goal.id)
                showDeleteDialog = false
            },
            onDismiss = { showDeleteDialog = false }
        )
    }
}

// ─── Delete Confirmation Dialog ──────────────────────────────────────────────
@Composable
private fun DeleteConfirmationDialog(
    goalTitle: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = BG_SURFACE)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(ERROR_LIGHT),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = ERROR_COLOR,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Delete Goal?",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = TEXT_PRIMARY
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Are you sure you want to delete \"$goalTitle\"? This action cannot be undone.",
                    fontSize = 14.sp,
                    color = TEXT_SECONDARY,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f).height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = ButtonDefaults.outlinedButtonBorder.copy(
                            width = 1.5.dp,
                            brush = Brush.linearGradient(
                                colors = listOf(DIVIDER_COLOR, DIVIDER_COLOR)
                            )
                        )
                    ) {
                        Text("Cancel", fontWeight = FontWeight.SemiBold)
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f).height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = ERROR_COLOR
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Delete", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ─── Add Goal Dialog ──────────────────────────────────────────────────────────
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
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BG_SURFACE)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header icon
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(ACCENT_PRIMARY, Color(0xFF8B5CF6))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Star,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Create New Goal",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TEXT_PRIMARY
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Let's build your roadmap to success",
                    fontSize = 13.sp,
                    color = TEXT_MUTED
                )

                Spacer(modifier = Modifier.height(20.dp))

                OutlinedTextField(
                    value = goalText,
                    onValueChange = {
                        goalText = it
                        showError = false
                    },
                    label = { Text("What's your goal?") },
                    placeholder = { Text("e.g., Learn Android Development") },
                    modifier = Modifier.fillMaxWidth(),
                    isError = showError,
                    supportingText = if (showError) {
                        { Text("Please enter a goal", color = ERROR_COLOR) }
                    } else null,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = ACCENT_PRIMARY,
                        unfocusedBorderColor = DIVIDER_COLOR
                    )
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Duration section
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Duration",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TEXT_SECONDARY
                        )
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(ACCENT_LIGHT)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "${days.toInt()} days",
                                fontWeight = FontWeight.Bold,
                                color = ACCENT_PRIMARY,
                                fontSize = 14.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Slider(
                        value = days,
                        onValueChange = { days = it },
                        valueRange = 1f..90f,
                        colors = SliderDefaults.colors(
                            thumbColor = ACCENT_PRIMARY,
                            activeTrackColor = ACCENT_PRIMARY,
                            inactiveTrackColor = ACCENT_LIGHT
                        )
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = {
                        if (goalText.isNotBlank()) {
                            onAdd(goalText, days.toInt())
                        } else {
                            showError = true
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ACCENT_PRIMARY
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Generate Roadmap",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

// ─── Roadmap Generation Dialog ────────────────────────────────────────────────
@Composable
fun RoadmapGenerationDialog(
    roadmapState: ViewState<Unit>,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = { if (roadmapState !is ViewState.Loading) onDismiss() }) {
        Card(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = BG_SURFACE)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (roadmapState) {
                    is ViewState.Loading -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(56.dp),
                            color = ACCENT_PRIMARY,
                            strokeWidth = 4.dp,
                            trackColor = ACCENT_LIGHT
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "Generating roadmap...",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = TEXT_PRIMARY
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "This may take a moment",
                            fontSize = 13.sp,
                            color = TEXT_MUTED
                        )
                    }

                    is ViewState.Success -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(SUCCESS_LIGHT),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = SUCCESS_COLOR,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Success!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TEXT_PRIMARY
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Your roadmap is ready",
                            fontSize = 14.sp,
                            color = TEXT_MUTED
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SUCCESS_COLOR
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Continue", fontWeight = FontWeight.Bold)
                        }
                    }

                    is ViewState.Error -> {
                        Box(
                            modifier = Modifier
                                .size(72.dp)
                                .clip(CircleShape)
                                .background(ERROR_LIGHT),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Error,
                                contentDescription = null,
                                tint = ERROR_COLOR,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "Oops!",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TEXT_PRIMARY
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            roadmapState.message,
                            fontSize = 14.sp,
                            color = TEXT_SECONDARY,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = ERROR_COLOR
                            ),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Close", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
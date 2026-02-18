package com.jaydeep.aimwise.ui.screens.roadmap

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jaydeep.aimwise.ui.components.FullScreenError
import com.jaydeep.aimwise.ui.components.FullScreenLoading
import com.jaydeep.aimwise.ui.screens.skip.SkipActionScreen
import com.jaydeep.aimwise.ui.state.ViewState
import com.jaydeep.aimwise.viewmodel.GoalViewModel
import kotlinx.coroutines.delay

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

private val TEXT_PRIMARY     = Color(0xFF1E1B4B)
private val TEXT_SECONDARY   = Color(0xFF475569)
private val TEXT_MUTED       = Color(0xFF94A3B8)
private val DIVIDER_COLOR    = Color(0xFFE2E8F0)

// ─── Main Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoadmapScreen(
    goalId: String,
    navController: NavHostController,
    viewModel: GoalViewModel,
) {
    LaunchedEffect(goalId) {
        android.util.Log.d("RoadmapScreen", "=== LaunchedEffect triggered for goal: $goalId ===")
        viewModel.loadRoadmap(goalId)
        delay(1000)
        viewModel.checkMissedDay(goalId)
    }

    val goalState by viewModel.goalState.collectAsState()
    val dayPlanState by viewModel.dayPlanState.collectAsState()
    val pending by viewModel.pendingAdjustment.collectAsState()
    val missedDay by viewModel.missedDay.collectAsState()
    val day by viewModel.currentDay.collectAsState()

    android.util.Log.d("RoadmapScreen", "Recomposition - pending: $pending, missedDay: $missedDay")

    val goalTitle by remember {
        derivedStateOf {
            (goalState as? ViewState.Success)?.data?.title ?: "Roadmap"
        }
    }

    // Skip adjustment screen
    if (pending && missedDay != null) {
        android.util.Log.d("RoadmapScreen", "✅ Showing SkipActionScreen for day: $missedDay")
        SkipActionScreen(goalId, missedDay!!, navController, viewModel)
        return
    } else {
        android.util.Log.d("RoadmapScreen", "Not showing SkipActionScreen - pending: $pending, missedDay: $missedDay")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.TrendingUp,
                            contentDescription = null,
                            tint = ACCENT_PRIMARY,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            goalTitle,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = BG_SURFACE
                )
            )
        },
        containerColor = BG_PRIMARY
    ) { paddingValues ->

        Box(modifier = Modifier.fillMaxSize()) {
            // Decorative background
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .offset(x = (-60).dp, y = (-80).dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(ACCENT_LIGHT.copy(0.5f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .align(Alignment.BottomEnd)
                    .offset(x = 60.dp, y = 60.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(SUCCESS_LIGHT.copy(0.4f), Color.Transparent)
                        ),
                        shape = CircleShape
                    )
            )

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

                    val currentDayValue = remember(day) { day ?: 1 }
                    val completedTasks = remember(dayPlan) { dayPlan.tasks.count { it.isCompleted } }
                    val totalTasks = remember(dayPlan) { dayPlan.tasks.size }
                    val taskProgress by remember(completedTasks, totalTasks) {
                        derivedStateOf {
                            if (totalTasks > 0) completedTasks.toFloat() / totalTasks else 0f
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Progress header
                        item {
                            ProgressHeader(
                                title = goal.title,
                                currentDay = currentDayValue,
                                totalDays = goal.durationDays,
                                completedTasks = completedTasks,
                                totalTasks = totalTasks,
                                progress = taskProgress
                            )
                        }

                        // View Full Roadmap button
                        item {
                            ViewRoadmapButton(
                                onClick = { navController.navigate("fullRoadmap/$goalId") }
                            )
                        }

                        // Today's Tasks header
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(ACCENT_PRIMARY)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Today's Tasks",
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = TEXT_PRIMARY
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(ACCENT_LIGHT)
                                        .padding(horizontal = 10.dp, vertical = 5.dp)
                                ) {
                                    Text(
                                        text = "$completedTasks / $totalTasks",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = ACCENT_PRIMARY
                                    )
                                }
                            }
                        }

                        // Task list
                        if (dayPlan.tasks.isEmpty()) {
                            item {
                                EmptyTasksState()
                            }
                        } else {
                            itemsIndexed(
                                items = dayPlan.tasks,
                                key = { index, _ -> index }
                            ) { index, task ->
                                TaskCard(
                                    task = task,
                                    index = index,
                                    animDelay = index * 40,
                                    onToggle = { viewModel.toggleTask(goalId, index) }
                                )
                            }
                        }

                        // Footer note
                        item {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = TEXT_MUTED,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Days automatically progress at midnight",
                                    fontSize = 12.sp,
                                    color = TEXT_MUTED
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Progress Header ──────────────────────────────────────────────────────────
@Composable
private fun ProgressHeader(
    title: String,
    currentDay: Int,
    totalDays: Int,
    completedTasks: Int,
    totalTasks: Int,
    progress: Float
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = ACCENT_PRIMARY.copy(0.12f),
                spotColor = ACCENT_PRIMARY.copy(0.18f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = BG_SURFACE),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(BG_SURFACE, BG_CARD_GRADIENT),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 600f)
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Day indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(ACCENT_LIGHT)
                            .border(1.dp, ACCENT_BORDER, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Today,
                                contentDescription = null,
                                tint = ACCENT_PRIMARY,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Day $currentDay",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = ACCENT_PRIMARY
                            )
                        }
                    }

                    Text(
                        text = "of $totalDays days",
                        fontSize = 13.sp,
                        color = TEXT_MUTED
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Task completion
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Today's Progress",
                            fontSize = 12.sp,
                            color = TEXT_MUTED,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "$completedTasks of $totalTasks tasks",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TEXT_PRIMARY
                        )
                    }

                    // Circular progress
                    Box(
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.size(56.dp),
                            color = ACCENT_PRIMARY,
                            strokeWidth = 5.dp,
                            trackColor = ACCENT_LIGHT
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = ACCENT_PRIMARY
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Linear progress bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(10.dp)
                        .clip(RoundedCornerShape(50))
                        .background(ACCENT_LIGHT)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(
                                brush = Brush.horizontalGradient(
                                    colors = listOf(ACCENT_PRIMARY, Color(0xFF8B5CF6))
                                )
                            )
                    )
                }
            }
        }
    }
}

// ─── View Roadmap Button ──────────────────────────────────────────────────────
@Composable
private fun ViewRoadmapButton(onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "buttonScale"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = SUCCESS_COLOR.copy(0.1f),
                spotColor = SUCCESS_COLOR.copy(0.15f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = SUCCESS_LIGHT
        ),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SUCCESS_COLOR),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.AccountTree,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "View Full Roadmap",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = SUCCESS_COLOR
                )
                Text(
                    text = "See all days and tasks",
                    fontSize = 12.sp,
                    color = TEXT_SECONDARY
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = SUCCESS_COLOR,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ─── Task Card ────────────────────────────────────────────────────────────────
@Composable
private fun TaskCard(
    task: com.jaydeep.aimwise.data.model.Task,
    index: Int,
    animDelay: Int,
    onToggle: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "taskScale"
    )

    val cardColor = if (task.isCompleted) SUCCESS_LIGHT else BG_SURFACE
    val borderColor = if (task.isCompleted) SUCCESS_BORDER else DIVIDER_COLOR

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = if (task.isCompleted) 4.dp else 6.dp,
                shape = RoundedCornerShape(16.dp),
                ambientColor = if (task.isCompleted)
                    SUCCESS_COLOR.copy(0.08f)
                else
                    ACCENT_PRIMARY.copy(0.06f),
                spotColor = if (task.isCompleted)
                    SUCCESS_COLOR.copy(0.12f)
                else
                    ACCENT_PRIMARY.copy(0.1f)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onToggle
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, borderColor, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Checkbox
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (task.isCompleted) SUCCESS_COLOR else Color.Transparent
                    )
                    .border(
                        width = 2.dp,
                        color = if (task.isCompleted) SUCCESS_COLOR else DIVIDER_COLOR,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = task.isCompleted,
                    enter = scaleIn(spring(Spring.DampingRatioMediumBouncy)) + fadeIn(),
                    exit = scaleOut() + fadeOut()
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Completed",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            // Task number and description
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .offset(y = 2.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (task.isCompleted) SUCCESS_COLOR.copy(0.15f) else ACCENT_LIGHT
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${index + 1}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = if (task.isCompleted) SUCCESS_COLOR else ACCENT_PRIMARY
                    )
                }

                Text(
                    text = task.description,
                    fontSize = 14.sp,
                    color = if (task.isCompleted) TEXT_SECONDARY else TEXT_PRIMARY,
                    lineHeight = 20.sp,
                    fontWeight = if (task.isCompleted) FontWeight.Normal else FontWeight.Medium
                )
            }
        }
    }
}

// ─── Empty Tasks State ────────────────────────────────────────────────────────
@Composable
private fun EmptyTasksState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(80.dp)
                .clip(CircleShape)
                .background(ACCENT_LIGHT),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = ACCENT_PRIMARY,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No tasks for today",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = TEXT_PRIMARY
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Enjoy your free day!",
            fontSize = 14.sp,
            color = TEXT_MUTED
        )
    }
}
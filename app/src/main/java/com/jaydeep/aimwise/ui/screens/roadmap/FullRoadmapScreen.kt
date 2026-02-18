package com.jaydeep.aimwise.ui.screens.roadmap

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jaydeep.aimwise.data.model.DayPlan
import com.jaydeep.aimwise.ui.components.FullScreenError
import com.jaydeep.aimwise.ui.components.FullScreenLoading
import com.jaydeep.aimwise.ui.state.ViewState
import com.jaydeep.aimwise.viewmodel.GoalViewModel
import kotlinx.coroutines.launch

// ─── Color Palette ────────────────────────────────────────────────────────────
private val BG_PRIMARY       = Color(0xFFF8FAFF)
private val BG_SURFACE       = Color(0xFFFFFFFF)

private val ACCENT_PRIMARY   = Color(0xFF6366F1)
private val ACCENT_LIGHT     = Color(0xFFEEF2FF)
private val ACCENT_BORDER    = Color(0xFFD4D8FF)

private val SUCCESS_COLOR    = Color(0xFF10B981)
private val SUCCESS_LIGHT    = Color(0xFFECFDF5)
private val SUCCESS_BORDER   = Color(0xFFA7F3D0)

private val CURRENT_COLOR    = Color(0xFFF59E0B)
private val CURRENT_LIGHT    = Color(0xFFFFF8EB)
private val CURRENT_BORDER   = Color(0xFFFFE4A0)

private val FUTURE_COLOR     = Color(0xFF94A3B8)
private val FUTURE_LIGHT     = Color(0xFFF1F5F9)
private val FUTURE_BORDER    = Color(0xFFE2E8F0)

private val TEXT_PRIMARY     = Color(0xFF1E1B4B)
private val TEXT_SECONDARY   = Color(0xFF475569)
private val TEXT_MUTED       = Color(0xFF94A3B8)
private val DIVIDER_COLOR    = Color(0xFFE2E8F0)

private val TIMELINE_LINE    = Color(0xFFD4D8FF)

// ─── Main Screen ──────────────────────────────────────────────────────────────
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullRoadmapScreen(
    goalId: String,
    navController: NavHostController,
    viewModel: GoalViewModel
) {
    val goalState by viewModel.goalState.collectAsState()
    val scope = rememberCoroutineScope()
    var allDays by remember { mutableStateOf<List<DayPlan>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(goalId) {
        viewModel.loadRoadmap(goalId)
    }

    LaunchedEffect(goalState) {
        if (goalState is ViewState.Success) {
            val goal = (goalState as ViewState.Success).data
            scope.launch {
                try {
                    isLoading = true
                    val days = mutableListOf<DayPlan>()
                    for (day in 1..goal.durationDays) {
                        val dayPlan = viewModel.getDayPlanForDay(goalId, day)
                        if (dayPlan != null) {
                            days.add(dayPlan)
                        }
                    }
                    allDays = days
                    isLoading = false
                } catch (e: Exception) {
                    errorMessage = e.message
                    isLoading = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.AccountTree,
                            contentDescription = null,
                            tint = ACCENT_PRIMARY,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            "Roadmap Timeline",
                            fontWeight = FontWeight.ExtraBold
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

        // Decorative background
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .offset(x = (-60).dp, y = (-60).dp)
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
                isLoading -> {
                    FullScreenLoading(message = "Loading roadmap...")
                }
                errorMessage != null -> {
                    FullScreenError(
                        message = errorMessage ?: "Unknown error",
                        onRetry = {
                            errorMessage = null
                            viewModel.loadRoadmap(goalId)
                        }
                    )
                }
                goalState is ViewState.Success -> {
                    val goal = (goalState as ViewState.Success).data

                    val totalCompletedTasks = allDays.sumOf { it.tasks.count { task -> task.isCompleted } }
                    val totalTasks = allDays.sumOf { it.tasks.size }
                    val progress = if (totalTasks > 0) totalCompletedTasks.toFloat() / totalTasks else 0f

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    ) {
                        // Header Card
                        RoadmapHeader(
                            title = goal.title,
                            currentDay = goal.currentDay,
                            totalDays = goal.durationDays,
                            completedTasks = totalCompletedTasks,
                            totalTasks = totalTasks,
                            progress = progress
                        )

                        // Timeline
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 20.dp,
                                end = 20.dp,
                                top = 16.dp,
                                bottom = 32.dp
                            )
                        ) {
                            itemsIndexed(
                                items = allDays,
                                key = { _, day -> day.day }
                            ) { index, dayPlan ->
                                TimelineDay(
                                    dayPlan = dayPlan,
                                    currentDay = goal.currentDay,
                                    isFirst = index == 0,
                                    isLast = index == allDays.lastIndex,
                                    animDelay = index * 30
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Roadmap Header ───────────────────────────────────────────────────────────
@Composable
private fun RoadmapHeader(
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
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .shadow(
                elevation = 12.dp,
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
                        colors = listOf(BG_SURFACE, Color(0xFFFAFBFF)),
                        start = Offset(0f, 0f),
                        end = Offset(1000f, 600f)
                    )
                )
        ) {
            Column(
                modifier = Modifier.padding(20.dp)
            ) {
                // Icon and title
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(ACCENT_PRIMARY, Color(0xFF8B5CF6))
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Flag,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TEXT_PRIMARY,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "$totalDays day journey",
                            fontSize = 13.sp,
                            color = TEXT_MUTED
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = DIVIDER_COLOR)
                Spacer(modifier = Modifier.height(16.dp))

                // Stats row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Current day badge
                    StatBadge(
                        icon = Icons.Rounded.Today,
                        label = "Day",
                        value = "$currentDay/$totalDays",
                        color = CURRENT_COLOR,
                        bgColor = CURRENT_LIGHT,
                        borderColor = CURRENT_BORDER,
                        modifier = Modifier.weight(1f)
                    )

                    // Tasks badge
                    StatBadge(
                        icon = Icons.Rounded.CheckCircle,
                        label = "Tasks",
                        value = "$completedTasks/$totalTasks",
                        color = SUCCESS_COLOR,
                        bgColor = SUCCESS_LIGHT,
                        borderColor = SUCCESS_BORDER,
                        modifier = Modifier.weight(1f)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Progress bar
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Overall Progress",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TEXT_SECONDARY
                        )
                        Text(
                            text = "${(progress * 100).toInt()}%",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = ACCENT_PRIMARY
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
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
}

// ─── Stat Badge ───────────────────────────────────────────────────────────────
@Composable
private fun StatBadge(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String,
    color: Color,
    bgColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = label,
                    fontSize = 10.sp,
                    color = TEXT_MUTED,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = value,
                    fontSize = 14.sp,
                    color = color,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

// ─── Timeline Day ─────────────────────────────────────────────────────────────
@Composable
private fun TimelineDay(
    dayPlan: DayPlan,
    currentDay: Int,
    isFirst: Boolean,
    isLast: Boolean,
    animDelay: Int
) {
    val completedTasks = dayPlan.tasks.count { it.isCompleted }
    val totalTasks = dayPlan.tasks.size
    val isCompleted = dayPlan.status == "completed"
    val isCurrent = dayPlan.day == currentDay
    val isFuture = dayPlan.day > currentDay

    // Determine colors based on state
    val (nodeColor, nodeBg, nodeBorder, cardBg) = when {
        isCompleted -> listOf(SUCCESS_COLOR, SUCCESS_LIGHT, SUCCESS_BORDER, SUCCESS_LIGHT.copy(0.3f))
        isCurrent -> listOf(CURRENT_COLOR, CURRENT_LIGHT, CURRENT_BORDER, CURRENT_LIGHT.copy(0.4f))
        else -> listOf(FUTURE_COLOR, FUTURE_LIGHT, FUTURE_BORDER, FUTURE_LIGHT.copy(0.5f))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = if (isLast) 0.dp else 0.dp)
    ) {
        // Timeline line & node
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(60.dp)
        ) {
            // Top line
            if (!isFirst) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(20.dp)
                        .background(
                            if (isCompleted || (isCurrent && completedTasks > 0))
                                SUCCESS_COLOR
                            else
                                TIMELINE_LINE
                        )
                )
            } else {
                Spacer(modifier = Modifier.height(20.dp))
            }

            // Node
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .shadow(
                        elevation = if (isCurrent) 8.dp else 4.dp,
                        shape = CircleShape,
                        ambientColor = nodeColor.copy(0.2f),
                        spotColor = nodeColor.copy(0.3f)
                    )
                    .clip(CircleShape)
                    .background(nodeBg)
                    .border(
                        width = if (isCurrent) 3.dp else 2.dp,
                        color = nodeBorder,
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (isCompleted) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Completed",
                        tint = SUCCESS_COLOR,
                        modifier = Modifier.size(28.dp)
                    )
                } else if (isCurrent) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(CURRENT_COLOR)
                    )
                } else {
                    Text(
                        text = "${dayPlan.day}",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = nodeColor
                    )
                }
            }

            // Bottom line
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .height(40.dp)
                        .background(
                            if (isCompleted)
                                SUCCESS_COLOR
                            else
                                TIMELINE_LINE
                        )
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Day card
        Card(
            modifier = Modifier
                .weight(1f)
                .padding(bottom = if (isLast) 0.dp else 20.dp)
                .shadow(
                    elevation = if (isCurrent) 10.dp else 6.dp,
                    shape = RoundedCornerShape(18.dp),
                    ambientColor = nodeColor.copy(0.08f),
                    spotColor = nodeColor.copy(0.12f)
                ),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = BG_SURFACE),
            elevation = CardDefaults.cardElevation(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(BG_SURFACE, cardBg),
                            start = Offset(0f, 0f),
                            end = Offset(600f, 400f)
                        )
                    )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Day ${dayPlan.day}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TEXT_PRIMARY
                            )
                            if (isCurrent) {
                                Spacer(modifier = Modifier.width(8.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(CURRENT_COLOR)
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        text = "TODAY",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                        }

                        // Progress badge
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(nodeBg)
                                .border(1.dp, nodeBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "$completedTasks/$totalTasks",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = nodeColor
                            )
                        }
                    }

                    if (dayPlan.tasks.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        HorizontalDivider(color = DIVIDER_COLOR.copy(0.5f))
                        Spacer(modifier = Modifier.height(10.dp))

                        // Task list
                        dayPlan.tasks.forEach { task ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(18.dp)
                                        .offset(y = 2.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (task.isCompleted) SUCCESS_LIGHT else FUTURE_LIGHT
                                        )
                                        .border(
                                            1.5.dp,
                                            if (task.isCompleted) SUCCESS_COLOR else FUTURE_BORDER,
                                            CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (task.isCompleted) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = SUCCESS_COLOR,
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = task.description,
                                    fontSize = 13.sp,
                                    color = if (task.isCompleted) TEXT_SECONDARY else TEXT_MUTED,
                                    lineHeight = 18.sp,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
package com.jaydeep.aimwise.ui.screens.skip

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.jaydeep.aimwise.viewmodel.GoalViewModel
import kotlinx.coroutines.delay

// ─── Palette ──────────────────────────────────────────────────────────────────
private val BG_PAGE         = Color(0xFFF0F4FF)
private val BG_CARD         = Color(0xFFFFFFFF)

private val VIOLET_SOFT     = Color(0xFF6366F1)
private val VIOLET_LIGHT    = Color(0xFFEEF2FF)
private val VIOLET_BORDER   = Color(0xFFD4D8FF)

private val AMBER_SOFT      = Color(0xFFF59E0B)
private val AMBER_LIGHT     = Color(0xFFFFF8EB)
private val AMBER_BORDER    = Color(0xFFFFE4A0)

private val EMERALD_SOFT    = Color(0xFF10B981)
private val EMERALD_LIGHT   = Color(0xFFECFDF5)
private val EMERALD_BORDER  = Color(0xFFA7F3D0)

private val ROSE_SOFT       = Color(0xFFF43F5E)
private val ROSE_LIGHT      = Color(0xFFFFF1F3)
private val ROSE_BORDER     = Color(0xFFFFBECA)

private val TEXT_HEADLINE   = Color(0xFF1E1B4B)
private val TEXT_BODY       = Color(0xFF374151)
private val TEXT_MUTED      = Color(0xFF9CA3AF)
private val DIVIDER         = Color(0xFFE5E7EB)

// ─── Option Data Class ────────────────────────────────────────────────────────
private data class ActionOption(
    val key: String,
    val icon: ImageVector,
    val label: String,
    val title: String,
    val description: String,
    val accent: Color,
    val bgLight: Color,
    val borderColor: Color,
    val gradientStart: Color,
    val gradientEnd: Color
)

private val actionOptions = listOf(
    ActionOption(
        key = "SKIP",
        icon = Icons.Rounded.SkipNext,
        label = "Quick",
        title = "Skip Day",
        description = "Keep your roadmap unchanged and continue from the next day seamlessly.",
        accent = VIOLET_SOFT,
        bgLight = VIOLET_LIGHT,
        borderColor = VIOLET_BORDER,
        gradientStart = Color(0xFFEEF2FF),
        gradientEnd = Color(0xFFF8F9FF)
    ),
    ActionOption(
        key = "MARK_COMPLETED",
        icon = Icons.Rounded.CheckCircle,
        label = "Recommended",
        title = "Mark Completed",
        description = "Mark this day as done and auto-redistribute tasks to upcoming days.",
        accent = EMERALD_SOFT,
        bgLight = EMERALD_LIGHT,
        borderColor = EMERALD_BORDER,
        gradientStart = Color(0xFFECFDF5),
        gradientEnd = Color(0xFFF8FFFC)
    ),
    ActionOption(
        key = "ADJUST_ROADMAP",
        icon = Icons.Rounded.Tune,
        label = "Smart",
        title = "Adjust Roadmap",
        description = "Intelligently reschedule incomplete tasks across your remaining days.",
        accent = AMBER_SOFT,
        bgLight = AMBER_LIGHT,
        borderColor = AMBER_BORDER,
        gradientStart = Color(0xFFFFF8EB),
        gradientEnd = Color(0xFFFFFDF7)
    )
)

// ─── Main Screen ──────────────────────────────────────────────────────────────
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
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var actionStartTime by remember { mutableStateOf(0L) }
    var incompleteTasks by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(goalId, missedDay) {
        try {
            incompleteTasks = viewModel.getIncompleteTasks(goalId, missedDay)
            android.util.Log.d("SkipActionScreen", "Loaded ${incompleteTasks.size} incomplete tasks")
        } catch (e: Exception) {
            android.util.Log.e("SkipActionScreen", "Error loading incomplete tasks: ${e.message}")
        }
    }

    val pendingAdjustment by viewModel.pendingAdjustment.collectAsState()

    LaunchedEffect(pendingAdjustment, loading) {
        if (loading) {
            val elapsed = System.currentTimeMillis() - actionStartTime
            android.util.Log.d("SkipActionScreen", "Loading state - pendingAdjustment: $pendingAdjustment, elapsed: ${elapsed}ms")
            if (!pendingAdjustment) {
                android.util.Log.d("SkipActionScreen", "Pending adjustment cleared, navigating back")
                delay(500)
                navController?.popBackStack()
            } else if (elapsed > 10000) {
                android.util.Log.e("SkipActionScreen", "Timeout, forcing navigation")
                navController?.popBackStack()
            }
        }
    }

    BackHandler(enabled = true) {}

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(Color(0xFFEEF2FF), Color(0xFFF5F7FF), Color(0xFFFFFBFF)),
                    start = Offset(0f, 0f),
                    end = Offset(1200f, 1800f)
                )
            )
    ) {
        // Decorative top-left blob
        Box(
            modifier = Modifier
                .size(300.dp)
                .offset(x = (-80).dp, y = (-80).dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x406366F1), Color(0x006366F1))
                    ),
                    shape = CircleShape
                )
        )
        // Decorative top-right blob
        Box(
            modifier = Modifier
                .size(220.dp)
                .align(Alignment.TopEnd)
                .offset(x = 70.dp, y = 20.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x3010B981), Color(0x0010B981))
                    ),
                    shape = CircleShape
                )
        )
        // Decorative bottom blob
        Box(
            modifier = Modifier
                .size(260.dp)
                .align(Alignment.BottomCenter)
                .offset(y = 80.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(Color(0x28F59E0B), Color(0x00F59E0B))
                    ),
                    shape = CircleShape
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {

            Spacer(modifier = Modifier.height(56.dp))

            // ── Header ───────────────────────────────────────────────────────
            HeaderSection(missedDay = missedDay, taskCount = incompleteTasks.size)

            Spacer(modifier = Modifier.height(28.dp))

            // ── Incomplete Tasks Card ────────────────────────────────────────
            AnimatedVisibility(
                visible = incompleteTasks.isNotEmpty(),
                enter = fadeIn(tween(400)) + expandVertically(tween(400))
            ) {
                Column {
                    IncompleteTasksCard(tasks = incompleteTasks)
                    Spacer(modifier = Modifier.height(20.dp))
                }
            }

            // ── Divider row ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(modifier = Modifier.weight(1f), color = DIVIDER)
                Text(
                    text = "  Choose an action  ",
                    color = TEXT_MUTED,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                HorizontalDivider(modifier = Modifier.weight(1f), color = DIVIDER)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // ── Action Cards ─────────────────────────────────────────────────
            actionOptions.forEachIndexed { index, option ->
                ActionCard(
                    option = option,
                    isSelected = selectedKey == option.key,
                    isDisabled = loading,
                    animDelay = index * 80,
                    onClick = {
                        if (!loading) {
                            android.util.Log.d("SkipActionScreen", "User selected: ${option.key}")
                            selectedKey = option.key
                            loading = true
                            actionStartTime = System.currentTimeMillis()
                            viewModel.resolveSkip(goalId, option.key)
                        }
                    }
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            Spacer(modifier = Modifier.height(100.dp))
        }

        // ── Loading Banner ────────────────────────────────────────────────────
        AnimatedVisibility(
            visible = loading,
            enter = fadeIn(tween(300)) + slideInVertically(tween(400)) { it },
            exit = fadeOut(tween(200)),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            LoadingBanner(selectedKey = selectedKey, options = actionOptions)
        }
    }
}

// ─── Header Section ───────────────────────────────────────────────────────────
@Composable
private fun HeaderSection(missedDay: Int, taskCount: Int) {
    // Pulsing warning icon
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = Modifier
            .size(72.dp)
            .scale(pulse)
            .clip(RoundedCornerShape(22.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(ROSE_LIGHT, Color(0xFFFFF0F2)),
                    start = Offset(0f, 0f),
                    end = Offset(72f, 72f)
                )
            )
            .border(1.5.dp, ROSE_BORDER, RoundedCornerShape(22.dp)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Rounded.Warning,
            contentDescription = null,
            tint = ROSE_SOFT,
            modifier = Modifier.size(36.dp)
        )
    }

    Spacer(modifier = Modifier.height(18.dp))

    // Missed day pill
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(ROSE_LIGHT)
            .border(1.dp, ROSE_BORDER, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.DateRange,
                contentDescription = null,
                tint = ROSE_SOFT,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = "Day $missedDay missed",
                color = ROSE_SOFT,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )
        }
    }

    Spacer(modifier = Modifier.height(14.dp))

    Text(
        text = "Let's get you\nback on track",
        color = TEXT_HEADLINE,
        fontWeight = FontWeight.ExtraBold,
        fontSize = 30.sp,
        lineHeight = 37.sp,
        textAlign = TextAlign.Center
    )

    Spacer(modifier = Modifier.height(10.dp))

    Text(
        text = if (taskCount > 0)
            "You have $taskCount incomplete task${if (taskCount > 1) "s" else ""}.\nChoose how you'd like to proceed."
        else
            "Choose how you'd like to\nadjust your roadmap.",
        color = TEXT_MUTED,
        fontSize = 15.sp,
        textAlign = TextAlign.Center,
        lineHeight = 22.sp
    )
}

// ─── Incomplete Tasks Card ────────────────────────────────────────────────────
@Composable
private fun IncompleteTasksCard(tasks: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 10.dp,
                shape = RoundedCornerShape(22.dp),
                ambientColor = VIOLET_SOFT.copy(alpha = 0.12f),
                spotColor = VIOLET_SOFT.copy(alpha = 0.18f)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = BG_CARD),
        elevation = CardDefaults.cardElevation(0.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(VIOLET_LIGHT)
                        .border(1.dp, VIOLET_BORDER, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.List,
                        contentDescription = null,
                        tint = VIOLET_SOFT,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Column {
                    Text(
                        text = "Incomplete Tasks",
                        color = TEXT_HEADLINE,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = "${tasks.size} task${if (tasks.size > 1) "s" else ""} need attention",
                        color = TEXT_MUTED,
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = DIVIDER)
            Spacer(modifier = Modifier.height(12.dp))

            tasks.forEachIndexed { index, task ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(VIOLET_LIGHT)
                            .border(1.dp, VIOLET_BORDER, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${index + 1}",
                            color = VIOLET_SOFT,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 11.sp
                        )
                    }
                    Text(
                        text = task,
                        color = TEXT_BODY,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// ─── Action Card ──────────────────────────────────────────────────────────────
@Composable
private fun ActionCard(
    option: ActionOption,
    isSelected: Boolean,
    isDisabled: Boolean,
    animDelay: Int,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cardScale"
    )

    val shadowElevation by animateDpAsState(
        targetValue = when {
            isSelected -> 14.dp
            isPressed  -> 4.dp
            else       -> 7.dp
        },
        animationSpec = tween(200),
        label = "elevation"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .shadow(
                elevation = shadowElevation,
                shape = RoundedCornerShape(22.dp),
                ambientColor = option.accent.copy(alpha = 0.14f),
                spotColor = option.accent.copy(alpha = 0.22f)
            )
            .clip(RoundedCornerShape(22.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(option.gradientStart, option.gradientEnd),
                    start = Offset(0f, 0f),
                    end = Offset(900f, 600f)
                )
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                brush = Brush.linearGradient(
                    colors = if (isSelected)
                        listOf(option.accent, option.accent.copy(alpha = 0.4f))
                    else
                        listOf(option.borderColor, DIVIDER)
                ),
                shape = RoundedCornerShape(22.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isDisabled,
                onClick = onClick
            )
            .padding(18.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Icon box
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(17.dp))
                    .background(option.bgLight)
                    .border(1.dp, option.borderColor, RoundedCornerShape(17.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (isSelected) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(26.dp),
                        color = option.accent,
                        strokeWidth = 2.5.dp,
                        trackColor = option.accent.copy(alpha = 0.2f)
                    )
                } else {
                    Icon(
                        imageVector = option.icon,
                        contentDescription = option.title,
                        tint = option.accent,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Text content
            Column(modifier = Modifier.weight(1f)) {
                // Label chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(option.bgLight)
                        .border(0.5.dp, option.borderColor, RoundedCornerShape(50))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = option.label.uppercase(),
                        color = option.accent,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = option.title,
                    color = TEXT_HEADLINE,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = option.description,
                    color = TEXT_MUTED,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            }

            // Chevron
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (isSelected) option.accent else option.bgLight)
                    .border(1.dp, option.borderColor, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else option.accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

// ─── Loading Banner ───────────────────────────────────────────────────────────
@Composable
private fun LoadingBanner(selectedKey: String?, options: List<ActionOption>) {
    val option = options.find { it.key == selectedKey }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 20.dp)
            .shadow(
                elevation = 18.dp,
                shape = RoundedCornerShape(20.dp),
                ambientColor = (option?.accent ?: VIOLET_SOFT).copy(alpha = 0.18f),
                spotColor = (option?.accent ?: VIOLET_SOFT).copy(alpha = 0.28f)
            )
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(
                        option?.gradientStart ?: VIOLET_LIGHT,
                        BG_CARD
                    )
                )
            )
            .border(
                1.dp,
                option?.borderColor ?: VIOLET_BORDER,
                RoundedCornerShape(20.dp)
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(30.dp),
                color = option?.accent ?: VIOLET_SOFT,
                strokeWidth = 3.dp,
                trackColor = option?.bgLight ?: VIOLET_LIGHT
            )
            Column {
                Text(
                    text = "Applying changes…",
                    color = TEXT_HEADLINE,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                Text(
                    text = "Updating your roadmap, please wait",
                    color = TEXT_MUTED,
                    fontSize = 12.sp
                )
            }
        }
    }
}
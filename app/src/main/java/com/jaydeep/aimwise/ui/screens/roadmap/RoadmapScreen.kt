import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jaydeep.aimwise.ui.screens.skip.SkipActionScreen
import com.jaydeep.aimwise.viewmodel.GoalViewModel

@Composable
fun RoadmapScreen(
    goalId: String,
    navController: NavHostController,

    viewModel: GoalViewModel = viewModel(),
) {

    val goal by viewModel.goal.collectAsState()
    val dayPlan by viewModel.dayPlan.collectAsState()
    val pending by viewModel.pendingAdjustment.collectAsState()
    val missedDay by viewModel.missedDay.collectAsState()
    val day by viewModel.currentDay.collectAsState()

    LaunchedEffect(day) {
        day?.let { viewModel.loadDay(goalId, it) }
    }

    LaunchedEffect(goalId) {
        viewModel.loadRoadmap(goalId)
        viewModel.checkMissedDay(goalId)
    }

    // 🚨 skip block
    if (pending && missedDay != null) {
        SkipActionScreen(
            goalId = goalId,
            missedDay = missedDay!!,
            navController = navController   // 👈 FIX
        )
        return
    }

    if (goal == null || dayPlan == null) {
        Text("Loading...")
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {

        Text(goal!!.title, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(8.dp))

        Text("Day ${goal!!.currentDay} of ${goal!!.durationDays}")

        Spacer(modifier = Modifier.height(12.dp))

        LinearProgressIndicator(
            progress = goal!!.currentDay.toFloat() / goal!!.durationDays
        )

        Spacer(modifier = Modifier.height(20.dp))

        dayPlan!!.tasks.forEachIndexed { index, task ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = dayPlan!!.completed[index],
                    onCheckedChange = {
                        viewModel.toggleTask(goalId, index)
                    }
                )
                Text(task)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Button(
            onClick = { viewModel.completeDay(goalId) }
        ) {
            Text("✔ Day Complete")
        }
    }
}




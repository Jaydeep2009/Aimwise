import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jaydeep.aimwise.viewmodel.GoalViewModel
val viewModel: GoalViewModel = GoalViewModel()

@Composable
fun RoadmapScreen(goal: String) {
    val pending by viewModel.pendingAdjustment.collectAsState()

    if (pending) {
        SkipActionScreen(goalId)
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(20.dp)
    ) {
        Text(
            text = "Roadmap",
            fontSize = 28.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = goal,
            fontSize = 18.sp
        )

        LaunchedEffect(Unit) {
            viewModel.checkMissedDay(goalId)
        }


        // Later:
        // show AI-generated roadmap here
    }
}

@Composable
fun SkipActionScreen(x0: goalId) {
    TODO("Not yet implemented")
}

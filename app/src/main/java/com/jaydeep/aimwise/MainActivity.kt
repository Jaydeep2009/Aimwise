package com.jaydeep.aimwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaydeep.aimwise.data.remote.RetrofitInstance
import com.jaydeep.aimwise.ui.navigation.AppNav
import com.jaydeep.aimwise.viewmodel.GoalViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val goalViewModel: GoalViewModel = viewModel()
            AppNav(goalViewModel)
        }
    }
}



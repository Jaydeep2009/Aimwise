package com.jaydeep.aimwise

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.LaunchedEffect
import com.jaydeep.aimwise.data.remote.RetrofitInstance
import com.jaydeep.aimwise.ui.navigation.AppNav

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            LaunchedEffect(Unit) {
                try {
                    RetrofitInstance.api.ping()
                } catch (_: Exception) {}
            }

            AppNav()
        }
    }
}



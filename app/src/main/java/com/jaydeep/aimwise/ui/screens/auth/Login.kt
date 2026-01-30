package com.jaydeep.aimwise.ui.screens.auth

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.jaydeep.aimwise.R
import com.jaydeep.aimwise.viewmodel.AuthViewModel


@Composable
    fun Login(navController: NavHostController) {
        val authViewModel: AuthViewModel =viewModel()
        val authResult by authViewModel.authStatus.observeAsState()
        val context = LocalContext.current
        var email by remember{ mutableStateOf("") }
        var password by remember{ mutableStateOf("") }


        authResult?.let{
            (success,message)->
        Toast.makeText(context,
            message?:"",Toast.LENGTH_SHORT).show()

        if(success){
            navController.navigate("home")
            {
                popUpTo("login") {
                    inclusive = true
                }
            }
        }
    }
        Column(modifier = Modifier
            .fillMaxSize()
            .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center)
        {

            Text(
                text = "Aimwise",
                fontSize = 36.sp,
                fontWeight=FontWeight.Bold,
                modifier=Modifier
                    .padding(10.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = {email=it},
                label = { androidx.compose.material3.Text(text = "Email") },
                textStyle = TextStyle(fontSize = 20.sp),
                modifier=Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
            )

            OutlinedTextField(
                value = password,
                onValueChange = {password=it},
                label = { androidx.compose.material3.Text(text = "Password") },
                modifier=Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
            )

            Button(onClick = {
                if(!email.contains("@gmail.com")){
                    Toast.makeText(
                        context,
                        "Please enter valid email",
                        Toast.LENGTH_SHORT).show()
                    email=""
                }else if(password.length<6){
                    Toast.makeText(
                        context,
                        "Password must be at least 6 characters",
                        Toast.LENGTH_SHORT).show()
                    password=""
                }else{
                    authViewModel.login(email,password)
                }
            },
                modifier=Modifier
                    .padding(10.dp)
                    .fillMaxWidth()) {
                Text("Login")

            }

            Text(
                text = "Don't have an account? Signup",
                modifier=Modifier
                    .padding(10.dp)
                    .fillMaxWidth()
                    .clickable {
                        navController.navigate("signup")
                    }
            )
        }
    }

package com.jaydeep.aimwise.ui.screens.auth

import android.widget.Toast
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jaydeep.aimwise.viewmodel.AuthViewModel


@Composable
@Preview(showSystemUi = true)
fun Signup(){
    val authViewModel: AuthViewModel=viewModel()
    val context = LocalContext.current
    var email by remember{ mutableStateOf("") }
    var password by remember{ mutableStateOf("") }
    var username by remember{ mutableStateOf("") }
    Column(modifier = Modifier
        .fillMaxSize()
        .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center)
    {
        OutlinedTextField(
            value = username,
            onValueChange = {username=it},
            label = { androidx.compose.material3.Text(text = "Username") },
            textStyle = TextStyle(fontSize = 20.sp),
            modifier=Modifier
                .padding(10.dp)
                .fillMaxWidth()
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
                authViewModel.signup(username,email,password)
            }
        },
            modifier=Modifier
                .padding(10.dp)
                .fillMaxWidth()) {
            Text("Signup")

        }
    }
}


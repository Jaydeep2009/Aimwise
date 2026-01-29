package com.jaydeep.aimwise.data.repository


import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import javax.security.auth.callback.Callback


class AuthRepository {
    private val auth= FirebaseAuth.getInstance()
    private val firestore= FirebaseFirestore.getInstance()

    fun curentUser()=auth.currentUser
    fun isUserLoggedIn() : Boolean=auth.currentUser!=null

     fun login(email:String,password:String,callback: (Boolean, String?) -> Unit){
        auth.signInWithEmailAndPassword(email,password)
            .addOnCompleteListener {
                task->
                if(task.isSuccessful){
                    callback(true,"Login successful")
                }else{
                    callback(false,"Login failed: ${task.exception?.message}")
                }
            }
    }

    fun signup(username:String,email: String,password: String,callback:(Boolean,String?)->Unit){
        auth.createUserWithEmailAndPassword(email,password)
            .addOnCompleteListener {task->
                if(task.isSuccessful){
                    val userId=auth.currentUser?.uid ?: ""
                    val userMap= hashMapOf(
                        "username" to username,
                        "email" to email
                    )
                    firestore.collection("users").document(userId)
                        .set(userMap)
                        .addOnCompleteListener {firestoreTask->
                            if(firestoreTask.isSuccessful){
                                callback(true,"Registration successful")
                            }else{
                                // Firestore write failed
                                callback(false,"Signup failed: ${firestoreTask.exception?.message}")
                                auth.currentUser?.delete()
                            }
                        }
                }else{
                    // Authentication failed
                    callback(false,"Signup failed: ${task.exception?.message}")
                }
            }
    }

    fun logout(){
        auth.signOut()
    }

    fun getCurrentUserId():String?{
        return auth.currentUser?.uid
    }
}
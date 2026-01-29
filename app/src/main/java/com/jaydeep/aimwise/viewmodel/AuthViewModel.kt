package com.jaydeep.aimwise.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.jaydeep.aimwise.data.repository.AuthRepository

class AuthViewModel : ViewModel() {

    private val repo = AuthRepository()

    // Private mutable state (only ViewModel can change it)
    private val _authStatus = MutableLiveData<Pair<Boolean, String?>>()

    // Public read-only state (UI can observe it)
    val authStatus: LiveData<Pair<Boolean, String?>> = _authStatus

    private val _isLoggedIn = MutableLiveData<Boolean>()
    val isLoggedIn: LiveData<Boolean> = _isLoggedIn

    init {
        _isLoggedIn.value = repo.isUserLoggedIn()
    }

    // 🔐 Login
    fun login(email: String, password: String) {
        repo.login(email, password) { success, message ->
            _authStatus.postValue(Pair(success, message))
            _isLoggedIn.postValue(success)
        }
    }

    // 📝 Signup
    fun signup(username: String, email: String, password: String) {
        repo.signup(username, email, password) { success, message ->
            _authStatus.postValue(Pair(success, message))
            _isLoggedIn.postValue(success)
        }
    }

    // 🚪 Logout
    fun logout() {
        repo.logout()
        _isLoggedIn.value = false
        _authStatus.value = Pair(false, "Logged out")
    }

    fun getCurrentUserId(): String? {
        return repo.getCurrentUserId()
    }
}

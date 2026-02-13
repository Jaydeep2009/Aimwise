package com.jaydeep.aimwise.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jaydeep.aimwise.data.model.Result
import com.jaydeep.aimwise.data.repository.AuthRepository
import com.jaydeep.aimwise.ui.state.ViewState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for managing authentication state and operations.
 * 
 * Handles user login, signup, and logout operations with proper state management.
 * Uses ViewState pattern to provide consistent loading, success, and error states
 * that the UI can observe and react to.
 * 
 * State flows:
 * - authState: Tracks the current authentication operation status
 * - isLoggedIn: Reflects whether a user is currently authenticated
 * 
 * @property repo The repository for authentication operations (injected for testability)
 */
class AuthViewModel(
    private val repo: AuthRepository = AuthRepository()
) : ViewModel() {

    // Private mutable state (only ViewModel can change it)
    private val _authState = MutableStateFlow<ViewState<Unit>>(ViewState.Loading)
    val authState: StateFlow<ViewState<Unit>> = _authState

    private val _isLoggedIn = MutableStateFlow<Boolean>(false)
    val isLoggedIn: StateFlow<Boolean> = _isLoggedIn

    init {
        _isLoggedIn.value = repo.isUserLoggedIn()
        // Set to idle state after checking login status
        _authState.value = ViewState.Success(Unit)
    }

    /**
     * Authenticates a user with email and password.
     * 
     * Updates authState through the following progression:
     * - Loading: Authentication in progress
     * - Success: User logged in successfully, isLoggedIn set to true
     * - Error: Authentication failed with error message
     * 
     * The UI should observe authState to show loading indicators and error messages.
     * 
     * @param email The user's email address
     * @param password The user's password
     */
    fun login(email: String, password: String) {
        viewModelScope.launch {
            _authState.value = ViewState.Loading
            when (val result = repo.login(email, password)) {
                is Result.Success -> {
                    _authState.value = ViewState.Success(Unit)
                    _isLoggedIn.value = true
                }
                is Result.Error -> {
                    _authState.value = ViewState.Error(
                        message = result.exception.message ?: "Login failed",
                        throwable = result.exception
                    )
                    _isLoggedIn.value = false
                }
                is Result.Loading -> {
                    // Loading state already set
                }
            }
        }
    }

    /**
     * Creates a new user account with username, email, and password.
     * 
     * Updates authState through the following progression:
     * - Loading: Account creation in progress
     * - Success: Account created and user logged in, isLoggedIn set to true
     * - Error: Signup failed with error message
     * 
     * If Firestore profile creation fails, the Firebase Auth user is automatically
     * deleted to maintain data consistency.
     * 
     * @param username The user's display name
     * @param email The user's email address
     * @param password The user's password
     */
    fun signup(username: String, email: String, password: String) {
        viewModelScope.launch {
            _authState.value = ViewState.Loading
            when (val result = repo.signup(username, email, password)) {
                is Result.Success -> {
                    _authState.value = ViewState.Success(Unit)
                    _isLoggedIn.value = true
                }
                is Result.Error -> {
                    _authState.value = ViewState.Error(
                        message = result.exception.message ?: "Signup failed",
                        throwable = result.exception
                    )
                    _isLoggedIn.value = false
                }
                is Result.Loading -> {
                    // Loading state already set
                }
            }
        }
    }

    /**
     * Logs out the current user and resets authentication state.
     * 
     * Clears the Firebase Auth session and updates isLoggedIn to false.
     * Resets authState to Success(Unit) to clear any previous error messages.
     */
    fun logout() {
        repo.logout()
        _isLoggedIn.value = false
        _authState.value = ViewState.Success(Unit)
    }

    /**
     * Resets the authentication state to clear any previous errors or success messages.
     * 
     * Should be called when navigating between login/signup screens to prevent
     * stale state from affecting the new screen.
     */
    fun resetAuthState() {
        _authState.value = ViewState.Success(Unit)
    }

    fun getCurrentUserId(): String? {
        return repo.getCurrentUserId()
    }
}

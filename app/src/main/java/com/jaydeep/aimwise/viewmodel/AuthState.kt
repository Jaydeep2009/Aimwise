package com.jaydeep.aimwise.viewmodel

data class AuthState(
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isUserLoggedIn: Boolean = false
)
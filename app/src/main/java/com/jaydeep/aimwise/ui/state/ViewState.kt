package com.jaydeep.aimwise.ui.state

sealed class ViewState<out T> {
    object Loading : ViewState<Nothing>()
    data class Success<T>(val data: T) : ViewState<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : ViewState<Nothing>()
}


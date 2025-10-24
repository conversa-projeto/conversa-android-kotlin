package com.example.conversaandroid.data.api

sealed class ApiResponse<T> {
    data class Success<T>(val data: T) : ApiResponse<T>()
    data class Error<T>(val message: String, val code: Int? = null) : ApiResponse<T>()
    class Loading<T> : ApiResponse<T>()
}
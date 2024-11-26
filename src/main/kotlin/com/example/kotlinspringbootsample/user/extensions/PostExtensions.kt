package com.example.kotlinspringbootsample.user.extensions

import com.example.kotlinspringbootsample.user.dto.SignupResponse
import com.example.kotlinspringbootsample.user.model.User


// User -> SignupResponse
fun User.toSignupResponse(): SignupResponse {
    return SignupResponse(
        username = this.username
    )
}
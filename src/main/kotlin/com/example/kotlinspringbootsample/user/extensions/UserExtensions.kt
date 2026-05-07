package com.example.kotlinspringbootsample.user.extensions

import com.example.kotlinspringbootsample.user.dto.SignupResponse
import com.example.kotlinspringbootsample.user.model.User

fun User.toSignupResponse(): SignupResponse =
    SignupResponse(
        username = this.username
    )

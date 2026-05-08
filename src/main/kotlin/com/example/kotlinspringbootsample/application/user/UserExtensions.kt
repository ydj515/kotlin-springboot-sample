package com.example.kotlinspringbootsample.application.user

import com.example.kotlinspringbootsample.application.user.result.SignupResult
import com.example.kotlinspringbootsample.domain.user.User

internal fun User.toSignupResult(): SignupResult =
    SignupResult(
        username = username
    )

package com.example.kotlinspringbootsample.presentation.auth

import com.example.kotlinspringbootsample.application.auth.AuthUseCase
import com.example.kotlinspringbootsample.application.auth.command.LoginCommand
import com.example.kotlinspringbootsample.application.auth.result.LoginResult
import com.example.kotlinspringbootsample.presentation.auth.request.LoginRequest
import com.example.kotlinspringbootsample.presentation.auth.response.LoginResponse
import com.example.kotlinspringbootsample.presentation.common.ApiResult
import com.example.kotlinspringbootsample.presentation.common.ResultCode
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authUseCase: AuthUseCase
) {
    @PostMapping("/login")
    fun login(
        @RequestBody request: LoginRequest,
        response: HttpServletResponse
    ): ApiResult.Success<LoginResponse> {
        val result = authUseCase.login(request.toCommand())
        response.setHeader(HttpHeaders.AUTHORIZATION, "${result.tokenType} ${result.accessToken}")
        return ApiResult.success(result.toResponse(), ResultCode.SUCCESS)
    }
}

private fun LoginRequest.toCommand(): LoginCommand =
    LoginCommand(
        username = username,
        password = password
    )

private fun LoginResult.toResponse(): LoginResponse =
    LoginResponse(
        username = username,
        tokenType = tokenType,
        accessToken = accessToken,
        accessTokenExpiresAt = accessTokenExpiresAt
    )

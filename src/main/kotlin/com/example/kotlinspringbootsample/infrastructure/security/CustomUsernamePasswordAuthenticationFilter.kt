package com.example.kotlinspringbootsample.infrastructure.security

import com.example.kotlinspringbootsample.presentation.auth.request.LoginRequest
import com.example.kotlinspringbootsample.presentation.common.ApiResult
import com.example.kotlinspringbootsample.presentation.common.ResultCode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.servlet.FilterChain
import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.AuthenticationServiceException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

class CustomUsernamePasswordAuthenticationFilter(
    authenticationManager: AuthenticationManager,
    private val tokenProvider: TokenProvider,
    private val objectMapper: ObjectMapper
) : UsernamePasswordAuthenticationFilter() {

    init {
        this.authenticationManager = authenticationManager
    }

    override fun attemptAuthentication(request: HttpServletRequest, response: HttpServletResponse): Authentication =
        runCatching { objectMapper.readValue<LoginRequest>(request.inputStream) }
            .map {
                UsernamePasswordAuthenticationToken(it.username, it.password)
            }
            .map(authenticationManager::authenticate)
            .getOrElse { throw AuthenticationServiceException("Failed to read request body", it) }

    override fun successfulAuthentication(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
        authResult: Authentication
    ) {
        response.status = HttpStatus.OK.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val loginResponse = tokenProvider.generateTokenDto(authResult)

        response.setHeader("Authorization", "Bearer ${loginResponse.accessToken}")

        loginResponse.refreshToken?.let { refreshToken ->
            val refreshTokenCookie = Cookie("refresh_token", refreshToken).apply {
                isHttpOnly = true
                secure = true
                path = AuthConstants.REFRESH_TOKEN_PATH
                maxAge = AuthConstants.MAX_AGE
            }
            response.addCookie(refreshTokenCookie)
        }

        val result = ApiResult.success(
            mapOf(AuthConstants.EXPIRES_KEY to loginResponse.accessTokenExpired),
            ResultCode.SUCCESS
        )

        response.writer.write(objectMapper.writeValueAsString(result))
    }
}

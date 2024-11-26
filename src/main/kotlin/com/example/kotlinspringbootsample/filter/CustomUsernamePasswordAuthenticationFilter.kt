package com.example.kotlinspringbootsample.filter

import com.example.kotlinspringbootsample.auth.AuthConstants.EXPIRES_KEY
import com.example.kotlinspringbootsample.auth.AuthConstants.MAX_AGE
import com.example.kotlinspringbootsample.auth.AuthConstants.REFRESH_TOKEN_PATH
import com.example.kotlinspringbootsample.auth.dto.LoginRequest
import com.example.kotlinspringbootsample.common.dto.ApiResponse
import com.example.kotlinspringbootsample.common.dto.ResultCode
import com.example.kotlinspringbootsample.config.security.TokenProvider
import com.example.kotlinspringbootsample.user.service.UserService
import com.fasterxml.jackson.databind.ObjectMapper
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
import org.springframework.stereotype.Component

@Component // ObjectMapper를 사용하기 위해 Component 등록
class CustomUsernamePasswordAuthenticationFilter(
    private val userService: UserService,
    authenticationManager: AuthenticationManager,
    private val tokenProvider: TokenProvider,
    private val objectMapper: ObjectMapper
) : UsernamePasswordAuthenticationFilter() {

    init {
        this.authenticationManager = authenticationManager
    }

    @Throws(AuthenticationException::class)
    override fun attemptAuthentication(request: HttpServletRequest, response: HttpServletResponse): Authentication {
        try {
            val creds = objectMapper.readValue(request.inputStream, LoginRequest::class.java)

            val authentication = UsernamePasswordAuthenticationToken(
                creds.username,
                creds.password
            )

            return authenticationManager.authenticate(authentication)
        } catch (e: Exception) {
            throw AuthenticationServiceException("Failed to read request body", e)
        }
    }

    @Throws(Exception::class)
    override fun successfulAuthentication(
        request: HttpServletRequest,
        response: HttpServletResponse,
        chain: FilterChain,
        authResult: Authentication
    ) {
        response.status = HttpStatus.OK.value()
        response.contentType = MediaType.APPLICATION_JSON_VALUE

        val loginResponse = tokenProvider.generateTokenDto(authResult)

        // Access Token -> Header에 추가
        response.setHeader("Authorization", "Bearer ${loginResponse.accessToken}")

        // Refresh Token -> Secure Cookie에 저장
        val refreshTokenCookie = Cookie("refresh_token", loginResponse.refreshToken).apply {
            isHttpOnly = true
            secure = true // https에서만 동작
            path = REFRESH_TOKEN_PATH
            maxAge = MAX_AGE
        }
        response.addCookie(refreshTokenCookie)

        val result = ApiResponse.of(
            mapOf(EXPIRES_KEY to loginResponse.accessTokenExpired),
            ResultCode.SUCCESS
        )

        response.writer.write(objectMapper.writeValueAsString(result))
    }
}

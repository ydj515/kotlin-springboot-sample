package com.example.kotlinspringbootsample.application.auth

import com.example.kotlinspringbootsample.application.auth.command.LoginCommand
import com.example.kotlinspringbootsample.application.auth.result.LoginResult
import com.example.kotlinspringbootsample.infrastructure.security.JwtTokenProvider
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.stereotype.Service

@Service
class AuthUseCase(
    private val authenticationManager: AuthenticationManager,
    private val jwtTokenProvider: JwtTokenProvider
) {
    fun login(command: LoginCommand): LoginResult {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(command.username, command.password)
        )
        val issuedAccessToken = jwtTokenProvider.issueAccessToken(authentication)

        return LoginResult(
            username = authentication.name,
            tokenType = issuedAccessToken.tokenType,
            accessToken = issuedAccessToken.accessToken,
            accessTokenExpiresAt = issuedAccessToken.accessTokenExpiresAt
        )
    }
}

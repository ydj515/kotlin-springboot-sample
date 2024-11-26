package com.example.kotlinspringbootsample.config.security

import com.example.kotlinspringbootsample.auth.service.AuthService
import org.springframework.context.annotation.Bean
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.AuthenticationException
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationManager(
    private val authService: AuthService
) : AuthenticationManager {

    @Bean
    protected fun passwordEncoder(): PasswordEncoder {
        return BCryptPasswordEncoder()
    }

    @Throws(AuthenticationException::class)
    override fun authenticate(authentication: Authentication): Authentication {
        val userDetails = authService.loadUserByUsername(authentication.name)
        if (!passwordEncoder().matches(authentication.credentials.toString(), userDetails.password)) {
            throw BadCredentialsException("Wrong password")
        }
        return UsernamePasswordAuthenticationToken(userDetails.username, userDetails.password, userDetails.authorities)
    }
}

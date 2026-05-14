package com.example.kotlinspringbootsample.infrastructure.security

import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class CustomAuthenticationManager(
    private val domainUserDetailsService: DomainUserDetailsService,
    private val passwordEncoder: PasswordEncoder
) : AuthenticationManager {

    override fun authenticate(authentication: Authentication): Authentication {
        val userDetails = domainUserDetailsService.loadUserByUsername(authentication.name)
        if (!passwordEncoder.matches(authentication.credentials.toString(), userDetails.password)) {
            throw BadCredentialsException("Wrong password")
        }

        return UsernamePasswordAuthenticationToken(
            userDetails.username,
            userDetails.password,
            userDetails.authorities
        )
    }
}

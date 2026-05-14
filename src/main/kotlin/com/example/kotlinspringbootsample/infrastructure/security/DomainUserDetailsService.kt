package com.example.kotlinspringbootsample.infrastructure.security

import com.example.kotlinspringbootsample.domain.user.exception.UserException
import com.example.kotlinspringbootsample.domain.user.service.UserLookupService
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class DomainUserDetailsService(
    private val userLookupService: UserLookupService
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails =
        try {
            userLookupService.requireByUsername(username)
                .let { user ->
                    User(
                        user.username,
                        user.password,
                        listOf(SimpleGrantedAuthority("ROLE_USER"))
                    )
                }
        } catch (exception: UserException) {
            throw UsernameNotFoundException("유효하지 않은 회원입니다.", exception)
        }
}

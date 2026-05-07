package com.example.kotlinspringbootsample.auth.service

import com.example.kotlinspringbootsample.user.repository.UserRepository
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository
) : UserDetailsService {

    override fun loadUserByUsername(username: String): UserDetails =
        userRepository.findByUsername(username)
            ?.let { member -> User(member.username, member.password, listOf(SimpleGrantedAuthority("ROLE_USER"))) }
            ?: throw UsernameNotFoundException("유효하지 않은 회원입니다.")
}

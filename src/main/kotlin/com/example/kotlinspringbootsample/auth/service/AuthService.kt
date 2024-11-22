package com.example.kotlinspringbootsample.auth.service


import com.example.kotlinspringbootsample.user.repository.UserRepository
import org.springframework.security.core.userdetails.User
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository
) : UserDetailsService {

    @Throws(UsernameNotFoundException::class)
    override fun loadUserByUsername(username: String): UserDetails {
        val member = userRepository.findUserByUsername(username)
            .orElseThrow { UsernameNotFoundException("유효하지 않은 회원입니다.") }

        return User.builder()
            .username(member.username)
            .password(member.password)
            .build()
    }

}

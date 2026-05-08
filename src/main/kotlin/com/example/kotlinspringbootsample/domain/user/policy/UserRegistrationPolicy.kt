package com.example.kotlinspringbootsample.domain.user.policy

import com.example.kotlinspringbootsample.domain.user.exception.UserAlreadyException
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRegistrationPolicy(
    private val userRepository: UserRepository
) {
    fun validateUsername(username: String) {
        if (userRepository.existsByUsername(username)) {
            throw UserAlreadyException()
        }
    }
}

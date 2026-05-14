package com.example.kotlinspringbootsample.domain.user.service

import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.exception.UserAlreadyException
import com.example.kotlinspringbootsample.domain.user.policy.UserRegistrationPolicy
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class UserRegistrationService(
    private val userRepository: UserRepository,
    private val userRegistrationPolicy: UserRegistrationPolicy
) {
    fun register(username: String, encodedPassword: String): User {
        val normalizedUsername = userRegistrationPolicy.normalizeUsername(username)

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw UserAlreadyException()
        }

        return userRepository.save(
            User(
                username = normalizedUsername,
                password = encodedPassword
            )
        )
    }
}

package com.example.kotlinspringbootsample.domain.user.policy

import com.example.kotlinspringbootsample.domain.user.exception.UserException
import org.springframework.stereotype.Component

@Component
class UserRegistrationPolicy {
    fun normalizeUsername(username: String): String {
        val normalizedUsername = username.trim()

        if (normalizedUsername.isBlank()) {
            throw UserException("username is required")
        }

        return normalizedUsername
    }
}

package com.example.kotlinspringbootsample.domain.user.service

import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.exception.UserException
import com.example.kotlinspringbootsample.domain.user.exception.UserNotFoundException
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class UserLookupService(
    private val userRepository: UserRepository
) {
    fun requireById(id: Long?): User {
        if (id == null) {
            throw UserException(message = "user id is required")
        }

        return userRepository.findById(id).orElseThrow {
            UserNotFoundException("user not found with id $id")
        }
    }

    fun requireByUsername(username: String?): User {
        if (username.isNullOrBlank()) {
            throw UserException(message = "username is required")
        }

        return userRepository.findByUsername(username)
            ?: throw UserNotFoundException("user not found with username $username")
    }
}

package com.example.kotlinspringbootsample.domain.user.service

import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.exception.UserException
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class UserLookupService(
    private val userRepository: UserRepository
) {
    fun requireByUsername(username: String): User =
        userRepository.findByUsername(username)
            ?: throw UserException(
                message = "user not found with username $username",
                statusCode = 404,
                errorCode = "404"
            )
}

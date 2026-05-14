package com.example.kotlinspringbootsample.domain.user.service

import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.exception.UserAlreadyException
import com.example.kotlinspringbootsample.domain.user.exception.UserException
import com.example.kotlinspringbootsample.domain.user.exception.UserNotFoundException
import com.example.kotlinspringbootsample.domain.user.policy.UserRegistrationPolicy
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import org.springframework.stereotype.Component

@Component
class UserDomainService(
    private val userRepository: UserRepository,
    private val userRegistrationPolicy: UserRegistrationPolicy
) {
    fun register(user: User): User {
        user.normalizeRegistrationFields()
        val normalizedUsername = userRegistrationPolicy.normalizeUsername(user.username)
        user.username = normalizedUsername

        if (userRepository.existsByUsername(normalizedUsername)) {
            throw UserAlreadyException()
        }

        return userRepository.save(user)
    }

    fun update(user: User): Int {
        val id = user.id ?: throw UserException(message = "user id is required for update")
        val existing = userRepository.findById(id).orElseThrow {
            UserNotFoundException("user not found with id $id")
        }

        existing.username = user.username
        existing.password = user.password.ifBlank { existing.password }
        existing.name = user.name
        existing.email = user.email
        existing.lastLoginAt = user.lastLoginAt ?: existing.lastLoginAt
        existing.deletedAt = user.deletedAt
        existing.lastPasswordUpdatedAt = user.lastPasswordUpdatedAt ?: existing.lastPasswordUpdatedAt
        existing.userType = user.userType
        existing.trialCount = user.trialCount

        userRepository.save(existing)
        return 1
    }

    fun deleteById(id: Long?): Int {
        if (id == null) {
            throw UserException(message = "user id is required for delete")
        }

        if (!userRepository.existsById(id)) {
            return 0
        }

        userRepository.deleteById(id)
        return 1
    }
}

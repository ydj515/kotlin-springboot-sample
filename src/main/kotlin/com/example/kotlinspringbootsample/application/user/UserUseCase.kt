package com.example.kotlinspringbootsample.application.user

import com.example.kotlinspringbootsample.application.user.command.CreateUserCommand
import com.example.kotlinspringbootsample.application.user.command.DeleteUserCommand
import com.example.kotlinspringbootsample.application.user.command.FindUsersCommand
import com.example.kotlinspringbootsample.application.user.command.GetUserByUsernameCommand
import com.example.kotlinspringbootsample.application.user.command.GetUserCommand
import com.example.kotlinspringbootsample.application.user.command.UpdateUserCommand
import com.example.kotlinspringbootsample.application.user.result.DeleteUserResult
import com.example.kotlinspringbootsample.application.user.result.UpdateUserResult
import com.example.kotlinspringbootsample.application.user.result.UserResult
import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import com.example.kotlinspringbootsample.domain.user.service.UserDomainService
import com.example.kotlinspringbootsample.domain.user.service.UserLookupService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
@Transactional(readOnly = true)
class UserUseCase(
    private val userRepository: UserRepository,
    private val userLookupService: UserLookupService,
    private val userDomainService: UserDomainService,
    private val passwordEncoder: PasswordEncoder
) {
    fun findAll(command: FindUsersCommand): List<UserResult> =
        userRepository.findAll().map(UserResult::from)

    fun findById(command: GetUserCommand): UserResult =
        UserResult.from(userLookupService.requireById(command.id))

    fun findByUsername(command: GetUserByUsernameCommand): UserResult =
        UserResult.from(userLookupService.requireByUsername(command.username))

    @Transactional
    fun create(command: CreateUserCommand): UserResult {
        val created = userDomainService.register(
            User.register(
                username = command.username,
                encodedPassword = passwordEncoder.encode(command.password),
                name = command.name,
                email = command.email,
                userType = command.userType,
                trialCount = command.trialCount
            )
        )
        return UserResult.from(created)
    }

    @Transactional
    fun update(command: UpdateUserCommand): UpdateUserResult {
        val user = User.restoreForUpdate(
            id = command.id,
            username = command.username,
            password = encodePassword(command.password),
            name = command.name,
            email = command.email,
            lastLoginAt = command.lastLoginAt,
            updatedAt = command.updatedAt,
            deletedAt = command.deletedAt,
            lastPasswordUpdatedAt = command.lastPasswordUpdatedAt,
            userType = command.userType,
            trialCount = command.trialCount
        )

        val updatedCount = userDomainService.update(user)
        return UpdateUserResult(id = command.id, updatedCount = updatedCount)
    }

    @Transactional
    fun delete(command: DeleteUserCommand): DeleteUserResult {
        val deletedCount = userDomainService.deleteById(command.id)
        return DeleteUserResult(id = command.id, deletedCount = deletedCount)
    }

    private fun encodePassword(rawPassword: String?): String? {
        if (rawPassword.isNullOrBlank()) {
            return null
        }
        return passwordEncoder.encode(rawPassword)
    }
}

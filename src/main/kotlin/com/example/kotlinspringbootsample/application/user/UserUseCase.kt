package com.example.kotlinspringbootsample.application.user

import com.example.kotlinspringbootsample.application.user.command.SignupCommand
import com.example.kotlinspringbootsample.application.user.result.SignupResult
import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.policy.UserRegistrationPolicy
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserUseCase(
    private val userRepository: UserRepository,
    private val userRegistrationPolicy: UserRegistrationPolicy,
    private val passwordEncoder: PasswordEncoder
) {
    fun registerMember(command: SignupCommand): SignupResult {
        userRegistrationPolicy.validateUsername(command.username)
        return User(
            username = command.username,
            password = passwordEncoder.encode(command.password)
        )
            .let(userRepository::save)
            .toSignupResult()
    }
}

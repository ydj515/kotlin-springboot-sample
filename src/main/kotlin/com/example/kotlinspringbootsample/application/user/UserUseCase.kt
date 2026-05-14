package com.example.kotlinspringbootsample.application.user

import com.example.kotlinspringbootsample.application.user.command.SignupCommand
import com.example.kotlinspringbootsample.application.user.result.SignupResult
import com.example.kotlinspringbootsample.domain.user.service.UserRegistrationService
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserUseCase(
    private val userRegistrationService: UserRegistrationService,
    private val passwordEncoder: PasswordEncoder
) {
    fun registerMember(command: SignupCommand): SignupResult =
        userRegistrationService.register(
            username = command.username,
            encodedPassword = passwordEncoder.encode(command.password)
        )
            .toSignupResult()
}

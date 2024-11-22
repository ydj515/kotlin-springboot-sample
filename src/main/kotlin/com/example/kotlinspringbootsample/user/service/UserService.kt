package com.example.kotlinspringbootsample.user.service

import com.example.kotlinspringbootsample.user.dto.SignupRequest
import com.example.kotlinspringbootsample.user.dto.SignupResponse
import com.example.kotlinspringbootsample.user.exception.UserAlreadyException
import com.example.kotlinspringbootsample.user.extensions.toSignupResponse
import com.example.kotlinspringbootsample.user.model.User
import com.example.kotlinspringbootsample.user.repository.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class UserService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) {
    fun registerMember(signUpRequest: SignupRequest): SignupResponse {
        if (userRepository.existsByUsername(signUpRequest.username)) {
            throw UserAlreadyException()
        }

        val encodedPassword = passwordEncoder.encode(signUpRequest.password)
        val user = User(
            username = signUpRequest.username,
            password = encodedPassword
        )

        val savedUser = userRepository.save(user)
        return savedUser.toSignupResponse()
    }

}

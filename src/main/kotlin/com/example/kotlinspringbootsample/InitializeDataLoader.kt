package com.example.kotlinspringbootsample

import com.example.kotlinspringbootsample.user.model.User
import com.example.kotlinspringbootsample.user.repository.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Component

@Component
class InitializeDataLoader(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : CommandLineRunner {

    override fun run(vararg args: String?) {
        userRepository.save(
            User(
                username = "test",
                password = passwordEncoder.encode("test")
            )
        )
    }
}

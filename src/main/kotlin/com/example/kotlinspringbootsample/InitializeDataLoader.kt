package com.example.kotlinspringbootsample

import com.example.kotlinspringbootsample.user.model.User
import com.example.kotlinspringbootsample.user.repository.UserRepository
import org.springframework.boot.CommandLineRunner
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Component

@Component
class InitializeDataLoader(private val userRepository: UserRepository) : CommandLineRunner {

    private val passwordEncoder = BCryptPasswordEncoder()

    override fun run(vararg args: String?) {
        val user = User(
            id = 1L,
            username = "test",
            password = passwordEncoder.encode("test")
        )

        userRepository.save(user)
    }
}
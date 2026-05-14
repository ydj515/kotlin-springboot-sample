package com.example.kotlinspringbootsample.domain.user.service

import com.example.kotlinspringbootsample.domain.user.exception.UserAlreadyException
import com.example.kotlinspringbootsample.domain.user.policy.UserRegistrationPolicy
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.mockk.every
import io.mockk.mockk

class UserRegistrationServiceTest : BehaviorSpec({

    val userRepository = mockk<UserRepository>()
    val userRegistrationPolicy = UserRegistrationPolicy()
    val userRegistrationService = UserRegistrationService(
        userRepository = userRepository,
        userRegistrationPolicy = userRegistrationPolicy
    )

    Given("회원가입 username이 이미 존재하면") {
        When("register를 호출하면") {
            Then("UserAlreadyException을 던진다") {
                every { userRepository.existsByUsername("alice") } returns true

                shouldThrow<UserAlreadyException> {
                    userRegistrationService.register(
                        username = "alice",
                        encodedPassword = "encoded-password"
                    )
                }
            }
        }
    }
})

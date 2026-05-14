package com.example.kotlinspringbootsample.domain.user.service

import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.UserType
import com.example.kotlinspringbootsample.domain.user.exception.UserAlreadyException
import com.example.kotlinspringbootsample.domain.user.exception.UserException
import com.example.kotlinspringbootsample.domain.user.exception.UserNotFoundException
import com.example.kotlinspringbootsample.domain.user.policy.UserRegistrationPolicy
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.util.Optional

class UserDomainServiceTest : BehaviorSpec({

    val userRepository = mockk<UserRepository>()
    val userRegistrationPolicy = UserRegistrationPolicy()
    val service = UserDomainService(userRepository, userRegistrationPolicy)

    Given("register: 이미 같은 username이 존재할 때") {
        When("register를 호출하면") {
            Then("UserAlreadyException을 던진다") {
                every { userRepository.existsByUsername("alice") } returns true

                shouldThrow<UserAlreadyException> {
                    service.register(
                        User.register(
                            username = "alice",
                            encodedPassword = "encoded"
                        )
                    )
                }
            }
        }
    }

    Given("register: username이 unique할 때") {
        When("register를 호출하면") {
            Then("normalize 후 저장한다") {
                val captured = slot<User>()
                every { userRepository.existsByUsername("bob") } returns false
                every { userRepository.save(capture(captured)) } answers { captured.captured.apply { id = 10L } }

                val saved = service.register(
                    User.register(
                        username = "  bob  ",
                        encodedPassword = "encoded",
                        name = "  Bob  ",
                        email = "  bob@example.com  ",
                        userType = UserType.USER,
                        trialCount = 2
                    )
                )

                saved.id shouldBe 10L
                captured.captured.username shouldBe "bob"
                captured.captured.name shouldBe "Bob"
                captured.captured.email shouldBe "bob@example.com"
                captured.captured.trialCount shouldBe 2
                captured.captured.userType shouldBe UserType.USER
            }
        }
    }

    Given("update: 존재하지 않는 id로 update를 호출하면") {
        When("update를 호출하면") {
            Then("UserNotFoundException을 던진다") {
                every { userRepository.findById(99L) } returns Optional.empty()

                shouldThrow<UserNotFoundException> {
                    service.update(
                        User.restoreForUpdate(
                            id = 99L,
                            username = "missing",
                            password = "p",
                            name = null,
                            email = null,
                            lastLoginAt = null,
                            updatedAt = null,
                            deletedAt = null,
                            lastPasswordUpdatedAt = null,
                            userType = null,
                            trialCount = 0
                        )
                    )
                }
            }
        }
    }

    Given("update: id가 null이면") {
        When("update를 호출하면") {
            Then("UserException을 던진다") {
                shouldThrow<UserException> {
                    val invalid = User(username = "x", password = "y")
                    service.update(invalid)
                }
            }
        }
    }

    Given("deleteById: 존재하는 id로 호출하면") {
        When("deleteById를 호출하면") {
            Then("1을 반환하고 repository.deleteById가 호출된다") {
                every { userRepository.existsById(5L) } returns true
                every { userRepository.deleteById(5L) } returns Unit

                service.deleteById(5L) shouldBe 1

                verify { userRepository.deleteById(5L) }
            }
        }
    }

    Given("deleteById: 존재하지 않는 id로 호출하면") {
        When("deleteById를 호출하면") {
            Then("0을 반환한다") {
                every { userRepository.existsById(404L) } returns false

                service.deleteById(404L) shouldBe 0
            }
        }
    }

    Given("deleteById: id가 null이면") {
        When("deleteById를 호출하면") {
            Then("UserException을 던진다") {
                shouldThrow<UserException> { service.deleteById(null) }
            }
        }
    }
})

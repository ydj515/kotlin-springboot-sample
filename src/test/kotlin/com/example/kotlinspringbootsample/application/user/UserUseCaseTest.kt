package com.example.kotlinspringbootsample.application.user

import com.example.kotlinspringbootsample.application.user.command.CreateUserCommand
import com.example.kotlinspringbootsample.application.user.command.DeleteUserCommand
import com.example.kotlinspringbootsample.application.user.command.FindUsersCommand
import com.example.kotlinspringbootsample.application.user.command.GetUserByUsernameCommand
import com.example.kotlinspringbootsample.application.user.command.GetUserCommand
import com.example.kotlinspringbootsample.application.user.command.UpdateUserCommand
import com.example.kotlinspringbootsample.domain.user.User
import com.example.kotlinspringbootsample.domain.user.UserType
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import com.example.kotlinspringbootsample.domain.user.service.UserDomainService
import com.example.kotlinspringbootsample.domain.user.service.UserLookupService
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.springframework.security.crypto.password.PasswordEncoder

class UserUseCaseTest : BehaviorSpec({

    val userRepository = mockk<UserRepository>()
    val userLookupService = mockk<UserLookupService>()
    val userDomainService = mockk<UserDomainService>()
    val passwordEncoder = mockk<PasswordEncoder>()
    val useCase = UserUseCase(userRepository, userLookupService, userDomainService, passwordEncoder)

    Given("findAll") {
        When("repository에 두 명의 사용자가 있을 때") {
            Then("두 UserResult를 매핑해 반환한다") {
                every { userRepository.findAll() } returns listOf(
                    User(id = 1L, username = "a", password = "p1"),
                    User(id = 2L, username = "b", password = "p2")
                )

                val result = useCase.findAll(FindUsersCommand.empty())
                result.map { it.username } shouldBe listOf("a", "b")
            }
        }
    }

    Given("findById") {
        When("정상 id로 조회하면") {
            Then("UserResult를 반환한다") {
                every { userLookupService.requireById(3L) } returns
                    User(id = 3L, username = "c", password = "p3")

                useCase.findById(GetUserCommand(3L)).username shouldBe "c"
            }
        }
    }

    Given("findByUsername") {
        When("정상 username으로 조회하면") {
            Then("UserResult를 반환한다") {
                every { userLookupService.requireByUsername("dora") } returns
                    User(id = 4L, username = "dora", password = "p4")

                useCase.findByUsername(GetUserByUsernameCommand("dora")).username shouldBe "dora"
            }
        }
    }

    Given("create") {
        When("CreateUserCommand가 주어지면") {
            Then("password를 인코딩하고 domain service.register를 호출한다") {
                val captured = slot<User>()
                every { passwordEncoder.encode("plain-pw") } returns "ENC(plain-pw)"
                every { userDomainService.register(capture(captured)) } answers {
                    captured.captured.apply { id = 99L }
                }

                val result = useCase.create(
                    CreateUserCommand(
                        username = "newuser",
                        password = "plain-pw",
                        name = "New User",
                        email = "new@example.com",
                        userType = UserType.USER,
                        trialCount = 0
                    )
                )

                result.id shouldBe 99L
                captured.captured.password shouldBe "ENC(plain-pw)"
                captured.captured.username shouldBe "newuser"
            }
        }
    }

    Given("update") {
        When("UpdateUserCommand에 password가 있으면") {
            Then("password를 인코딩한 후 domain service.update를 호출한다") {
                val captured = slot<User>()
                every { passwordEncoder.encode("new-pw") } returns "ENC(new-pw)"
                every { userDomainService.update(capture(captured)) } returns 1

                val result = useCase.update(
                    UpdateUserCommand(
                        id = 7L,
                        username = "user7",
                        password = "new-pw",
                        userType = UserType.MANAGER,
                        trialCount = 3
                    )
                )

                result.id shouldBe 7L
                result.updatedCount shouldBe 1
                captured.captured.password shouldBe "ENC(new-pw)"
            }
        }

        When("UpdateUserCommand에 password가 비어있으면") {
            Then("password 인코딩 없이 빈 값으로 domain service.update를 호출한다") {
                clearMocks(passwordEncoder, answers = false)
                val captured = slot<User>()
                every { userDomainService.update(capture(captured)) } returns 1

                useCase.update(UpdateUserCommand(id = 8L, username = "user8"))

                captured.captured.password shouldBe ""
                verify(exactly = 0) { passwordEncoder.encode(any()) }
            }
        }
    }

    Given("delete") {
        When("DeleteUserCommand가 주어지면") {
            Then("domain service.deleteById를 호출하고 deletedCount를 반환한다") {
                every { userDomainService.deleteById(11L) } returns 1

                val result = useCase.delete(DeleteUserCommand(11L))
                result.id shouldBe 11L
                result.deletedCount shouldBe 1
            }
        }
    }
})

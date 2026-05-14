package com.example.kotlinspringbootsample.application.user

import com.example.kotlinspringbootsample.application.user.command.CreateUserCommand
import com.example.kotlinspringbootsample.application.user.command.DeleteUserCommand
import com.example.kotlinspringbootsample.application.user.command.GetUserCommand
import com.example.kotlinspringbootsample.application.user.command.UpdateUserCommand
import com.example.kotlinspringbootsample.application.user.result.UserResult
import com.example.kotlinspringbootsample.domain.user.UserType
import com.example.kotlinspringbootsample.domain.user.exception.UserNotFoundException
import com.example.kotlinspringbootsample.domain.user.repository.UserRepository
import com.example.kotlinspringbootsample.support.MySqlIntegrationTestSupport
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.security.crypto.password.PasswordEncoder

class UserUseCaseIntegrationTest @Autowired constructor(
    private val userUseCase: UserUseCase,
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder
) : MySqlIntegrationTestSupport() {
    private val createdUserIds = mutableListOf<Long>()

    private fun createUser(
        username: String,
        password: String = "init-pass",
        name: String? = "Initial",
        email: String? = "init@example.com",
        userType: UserType? = UserType.USER,
        trialCount: Int = 0
    ): UserResult {
        val suffix = System.nanoTime()
        val uniqueUsername = "$username-$suffix"
        val result = userUseCase.create(
            CreateUserCommand(
                username = uniqueUsername,
                password = password,
                name = name,
                email = email,
                userType = userType,
                trialCount = trialCount
            )
        )
        result.id?.let(createdUserIds::add)
        return result
    }

    @AfterEach
    fun cleanup() {
        createdUserIds.forEach { id ->
            if (userRepository.existsById(id)) {
                userRepository.deleteById(id)
            }
        }
        createdUserIds.clear()
    }

    @Test
    fun `update는 기존 사용자의 name, email, userType, trialCount를 갱신한다`() {
        val created = createUser(
            username = "update-target",
            name = "Initial Name",
            email = "initial@example.com",
            trialCount = 0
        )
        val createdId = created.id!!

        val updated = userUseCase.update(
            UpdateUserCommand(
                id = createdId,
                username = created.username,
                password = null,
                name = "Updated Name",
                email = "updated@example.com",
                userType = UserType.MANAGER,
                trialCount = 3
            )
        )

        assertThat(updated.id).isEqualTo(createdId)
        assertThat(updated.updatedCount).isEqualTo(1)

        val fetched = userUseCase.findById(GetUserCommand(createdId))
        assertThat(fetched.name).isEqualTo("Updated Name")
        assertThat(fetched.email).isEqualTo("updated@example.com")
        assertThat(fetched.userType).isEqualTo("MANAGER")
        assertThat(fetched.trialCount).isEqualTo(3)
    }

    @Test
    fun `update에 password가 주어지면 인코딩되어 저장되고, 없으면 기존 password가 유지된다`() {
        val created = createUser(
            username = "password-target",
            password = "first-pass"
        )
        val createdId = created.id!!
        val initialPassword = userRepository.findById(createdId).orElseThrow().password

        userUseCase.update(
            UpdateUserCommand(
                id = createdId,
                username = created.username,
                password = null,
                name = "P2",
                email = "p2@example.com"
            )
        )
        val afterNullPassword = userRepository.findById(createdId).orElseThrow().password
        assertThat(afterNullPassword).isEqualTo(initialPassword)

        userUseCase.update(
            UpdateUserCommand(
                id = createdId,
                username = created.username,
                password = "second-pass",
                name = "P3",
                email = "p3@example.com"
            )
        )
        val afterChange = userRepository.findById(createdId).orElseThrow().password
        assertThat(afterChange).isNotEqualTo(initialPassword)
        assertThat(passwordEncoder.matches("second-pass", afterChange)).isTrue()
    }

    @Test
    fun `delete는 존재하는 사용자를 제거하고 deletedCount 1을 반환한다`() {
        val created = createUser(username = "delete-target")
        val createdId = created.id!!

        val result = userUseCase.delete(DeleteUserCommand(createdId))

        assertThat(result.deletedCount).isEqualTo(1)
        assertThat(userRepository.findById(createdId)).isEmpty
    }

    @Test
    fun `delete는 존재하지 않는 id에 대해 deletedCount 0을 반환한다`() {
        val result = userUseCase.delete(DeleteUserCommand(999_999_999L))

        assertThat(result.deletedCount).isEqualTo(0)
    }

    @Test
    fun `delete 후 findById를 호출하면 UserNotFoundException을 던진다`() {
        val created = createUser(username = "delete-then-find")
        val createdId = created.id!!

        userUseCase.delete(DeleteUserCommand(createdId))

        assertThatThrownBy { userUseCase.findById(GetUserCommand(createdId)) }
            .isInstanceOf(UserNotFoundException::class.java)
    }
}

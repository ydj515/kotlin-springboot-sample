package com.example.kotlinspringbootsample.user.repository

import com.example.kotlinspringbootsample.user.model.User
import org.springframework.data.jpa.repository.JpaRepository
import java.util.*

interface UserRepository : JpaRepository<User, Long> {
    fun findUserByUsername(username: String): Optional<User>
}

package com.example.kotlinspringbootsample.domain.user.repository

import com.example.kotlinspringbootsample.domain.user.User
import org.springframework.data.jpa.repository.JpaRepository

interface UserRepository : JpaRepository<User, Long> {
    fun findByUsername(username: String): User?
    fun existsByUsername(username: String): Boolean
}

package com.example.kotlinspringbootsample.domain.user.repository

import com.example.kotlinspringbootsample.domain.user.Role
import org.springframework.data.jpa.repository.JpaRepository

interface RoleRepository : JpaRepository<Role, Long> {
    fun findByName(name: String): Role?
}

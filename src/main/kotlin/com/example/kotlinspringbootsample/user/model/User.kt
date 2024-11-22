package com.example.kotlinspringbootsample.user.model

import com.example.kotlinspringbootsample.common.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(unique = true, nullable = false)
    var username: String? = null,

    @Column(nullable = false)
    var password: String? = null,

    @Column
    var deletedAt: LocalDateTime? = null,

    ) : BaseEntity()

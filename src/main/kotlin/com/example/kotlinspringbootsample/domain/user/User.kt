package com.example.kotlinspringbootsample.domain.user

import com.example.kotlinspringbootsample.common.entity.BaseEntity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.JoinTable
import jakarta.persistence.ManyToMany
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "users")
class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    @Column(unique = true, nullable = false)
    var username: String,

    @Column(nullable = false)
    var password: String,

    @Column
    var name: String? = null,

    @Column
    var email: String? = null,

    @Column(name = "last_login_at")
    var lastLoginAt: LocalDateTime? = null,

    @Column(name = "deleted_at")
    var deletedAt: LocalDateTime? = null,

    @Column(name = "last_password_updated_at")
    var lastPasswordUpdatedAt: LocalDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "user_type")
    var userType: UserType? = null,

    @Column(name = "trial_count", nullable = false)
    var trialCount: Int = 0,

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "user_roles",
        joinColumns = [JoinColumn(name = "user_id")],
        inverseJoinColumns = [JoinColumn(name = "role_id")]
    )
    var roles: MutableSet<Role> = mutableSetOf()
) : BaseEntity() {

    fun normalizeRegistrationFields() {
        username = username.trim()
        name = name?.trim()
        email = email?.trim()
    }

    companion object {
        fun register(
            username: String,
            encodedPassword: String,
            name: String? = null,
            email: String? = null,
            userType: UserType? = null,
            trialCount: Int = 0
        ): User = User(
            username = username,
            password = encodedPassword,
            name = name,
            email = email,
            userType = userType,
            trialCount = trialCount
        )

        fun restoreForUpdate(
            id: Long,
            username: String,
            password: String?,
            name: String?,
            email: String?,
            lastLoginAt: LocalDateTime?,
            updatedAt: LocalDateTime?,
            deletedAt: LocalDateTime?,
            lastPasswordUpdatedAt: LocalDateTime?,
            userType: UserType?,
            trialCount: Int
        ): User = User(
            id = id,
            username = username,
            password = password ?: "",
            name = name,
            email = email,
            lastLoginAt = lastLoginAt,
            deletedAt = deletedAt,
            lastPasswordUpdatedAt = lastPasswordUpdatedAt,
            userType = userType,
            trialCount = trialCount
        ).also {
            it.updatedAt = updatedAt
        }
    }
}

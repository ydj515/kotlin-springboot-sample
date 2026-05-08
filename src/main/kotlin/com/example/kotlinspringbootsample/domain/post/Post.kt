package com.example.kotlinspringbootsample.domain.post

import com.example.kotlinspringbootsample.common.entity.BaseEntity
import jakarta.persistence.*
import java.time.LocalDateTime

@Entity
@Table(name = "posts")
class Post(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var title: String,
    var content: String,
    var username: String,
    var password: String,
    var deletedAt: LocalDateTime? = null
) : BaseEntity() {
    fun update(title: String, content: String, username: String, password: String) = apply {
        this.title = title
        this.content = content
        this.username = username
        this.password = password
    }

    fun markDeleted(deletedAt: LocalDateTime = LocalDateTime.now()) {
        this.deletedAt = deletedAt
    }
}

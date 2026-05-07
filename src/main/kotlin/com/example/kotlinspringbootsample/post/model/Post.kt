package com.example.kotlinspringbootsample.post.model

import com.example.kotlinspringbootsample.common.entity.BaseEntity
import com.example.kotlinspringbootsample.post.dto.PostRequest
import com.example.kotlinspringbootsample.post.exception.PostNotFoundException
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
    fun updateFromRequest(postRequest: PostRequest) = apply {
        title = postRequest.title
        content = postRequest.content
        username = postRequest.username
        password = postRequest.password
    }

    fun requireAuthor(username: String, password: String) {
        if (this.username != username || this.password != password) {
            throw PostNotFoundException("username or password invalid")
        }
    }

    fun markDeleted(deletedAt: LocalDateTime = LocalDateTime.now()) {
        this.deletedAt = deletedAt
    }
}

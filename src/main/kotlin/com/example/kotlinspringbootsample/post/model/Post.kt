package com.example.kotlinspringbootsample.post.model

import com.example.kotlinspringbootsample.common.entity.BaseEntity
import com.example.kotlinspringbootsample.post.dto.PostRequest
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
    fun updateFromRequest(postRequest: PostRequest) {
        this.title = postRequest.title
        this.content = postRequest.content
        this.username = postRequest.username
        this.password = postRequest.password
    }
    fun delete() {
        this.deletedAt = LocalDateTime.now() // 현재 시간으로 deleted_at 설정
    }
}
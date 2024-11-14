package com.example.kotlinspringbootsample.post.model

import com.example.kotlinspringbootsample.common.entity.BaseEntity
import jakarta.persistence.*

@Entity
@Table(name = "posts")
class Post(

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null,

    var title: String,
    var content: String,
    var username: String,
    var password: String
) : BaseEntity()
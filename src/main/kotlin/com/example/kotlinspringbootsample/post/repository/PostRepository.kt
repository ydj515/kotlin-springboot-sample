package com.example.kotlinspringbootsample.post.repository

import com.example.kotlinspringbootsample.post.model.Post
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PostRepository : JpaRepository<Post, Long> {
}
package com.example.kotlinspringbootsample.domain.post.repository

import com.example.kotlinspringbootsample.domain.post.Post
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface PostRepository : JpaRepository<Post, Long> {
    fun findAllByDeletedAtIsNull(pageable: Pageable): Page<Post>
}

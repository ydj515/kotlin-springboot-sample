package com.example.kotlinspringbootsample.user.controller

import com.example.kotlinspringbootsample.user.dto.SignupRequest
import com.example.kotlinspringbootsample.user.dto.SignupResponse
import com.example.kotlinspringbootsample.user.service.UserService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("")
class MemberController(private val userService: UserService) {
    @PostMapping("/signup")
    fun signUp(@RequestBody signUpRequest: SignupRequest): ResponseEntity<SignupResponse> {
        val signupResponse = userService.registerMember(signUpRequest)
        return ResponseEntity.status(HttpStatus.CREATED).body(signupResponse)
    }
}

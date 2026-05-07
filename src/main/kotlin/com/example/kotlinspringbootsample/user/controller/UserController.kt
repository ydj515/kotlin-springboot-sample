package com.example.kotlinspringbootsample.user.controller

import com.example.kotlinspringbootsample.common.dto.ApiResult
import com.example.kotlinspringbootsample.common.dto.ResultCode
import com.example.kotlinspringbootsample.user.dto.SignupRequest
import com.example.kotlinspringbootsample.user.dto.SignupResponse
import com.example.kotlinspringbootsample.user.service.UserService
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.ResponseStatus

@RestController
@RequestMapping("")
class UserController(private val userService: UserService) {
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    fun signUp(@Valid @RequestBody signUpRequest: SignupRequest): ApiResult.Success<SignupResponse> =
        ApiResult.success(userService.registerMember(signUpRequest), ResultCode.CREATED)
}

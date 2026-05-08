package com.example.kotlinspringbootsample.presentation.user

import com.example.kotlinspringbootsample.application.user.UserUseCase
import com.example.kotlinspringbootsample.application.user.command.SignupCommand
import com.example.kotlinspringbootsample.application.user.result.SignupResult
import com.example.kotlinspringbootsample.config.SwaggerRefs.SIGNUP_REQUEST_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.SIGNUP_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.presentation.common.ApiResult
import com.example.kotlinspringbootsample.presentation.common.ResultCode
import com.example.kotlinspringbootsample.presentation.user.request.SignupRequest
import com.example.kotlinspringbootsample.presentation.user.response.SignupResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("")
@Tag(name = "Users", description = "회원가입 API")
class UserController(private val userUseCase: UserUseCase) {
    @Operation(summary = "회원가입", description = "새 사용자를 등록합니다.")
    @SwaggerRequestBody(
        required = true,
        description = "회원가입 요청 바디",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = SignupRequest::class),
            examples = [ExampleObject(ref = SIGNUP_REQUEST_EXAMPLE_REF)]
        )]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "회원가입 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = SIGNUP_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping("/signup")
    fun signUp(@Valid @RequestBody signUpRequest: SignupRequest): ApiResult.Success<SignupResponse> =
        ApiResult.success(userUseCase.registerMember(signUpRequest.toCommand()).toResponse(), ResultCode.CREATED)
}

private fun SignupRequest.toCommand(): SignupCommand =
    SignupCommand(
        username = username,
        password = password
    )

private fun SignupResult.toResponse(): SignupResponse =
    SignupResponse(username = username)

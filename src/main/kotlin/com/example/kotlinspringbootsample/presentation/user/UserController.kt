package com.example.kotlinspringbootsample.presentation.user

import com.example.kotlinspringbootsample.application.user.UserUseCase
import com.example.kotlinspringbootsample.application.user.command.FindUsersCommand
import com.example.kotlinspringbootsample.application.user.command.GetUserCommand
import com.example.kotlinspringbootsample.config.SwaggerRefs.SIGNUP_REQUEST_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.SIGNUP_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.presentation.common.ApiResult
import com.example.kotlinspringbootsample.presentation.common.ResultCode
import com.example.kotlinspringbootsample.presentation.user.request.CreateUserRequest
import com.example.kotlinspringbootsample.presentation.user.request.DeleteUserRequest
import com.example.kotlinspringbootsample.presentation.user.request.UpdateUserRequest
import com.example.kotlinspringbootsample.presentation.user.request.UserSearchRequest
import com.example.kotlinspringbootsample.presentation.user.response.DeleteUserResponse
import com.example.kotlinspringbootsample.presentation.user.response.UpdateUserResponse
import com.example.kotlinspringbootsample.presentation.user.response.UserResponse
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
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/users")
@Tag(name = "Users", description = "사용자 CRUD API")
class UserController(private val userUseCase: UserUseCase) {

    @Operation(summary = "사용자 목록 조회", description = "전체 사용자 목록을 조회합니다.")
    @GetMapping
    fun getUsers(): ApiResult.Success<List<UserResponse>> =
        ApiResult.success(
            userUseCase.findAll(FindUsersCommand.empty()).map(UserResponse::from)
        )

    @Operation(summary = "사용자 단건 조회", description = "id로 사용자 단건을 조회합니다.")
    @GetMapping("/{id}")
    fun getUser(@PathVariable id: Long): ApiResult.Success<UserResponse> =
        ApiResult.success(UserResponse.from(userUseCase.findById(GetUserCommand(id))))

    @Operation(summary = "username으로 사용자 조회", description = "쿼리 파라미터의 username으로 사용자를 조회합니다.")
    @GetMapping("/detail")
    fun getUserByUsername(@ModelAttribute request: UserSearchRequest): ApiResult.Success<UserResponse> =
        ApiResult.success(UserResponse.from(userUseCase.findByUsername(request.toCommand())))

    @Operation(summary = "회원가입", description = "새 사용자를 등록합니다.")
    @SwaggerRequestBody(
        required = true,
        description = "회원가입 요청 바디",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = CreateUserRequest::class),
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
    @PostMapping
    fun createUser(@Valid @RequestBody request: CreateUserRequest): ApiResult.Success<UserResponse> =
        ApiResult.success(UserResponse.from(userUseCase.create(request.toCommand())), ResultCode.CREATED)

    @Operation(summary = "사용자 수정", description = "전체 필드를 받아 사용자를 수정합니다.")
    @PutMapping
    fun updateUser(@RequestBody request: UpdateUserRequest): ApiResult.Success<UpdateUserResponse> =
        ApiResult.success(UpdateUserResponse.from(userUseCase.update(request.toCommand())))

    @Operation(summary = "사용자 삭제", description = "id로 사용자를 삭제합니다.")
    @DeleteMapping("/{id}")
    fun deleteUser(@PathVariable id: Long): ApiResult.Success<DeleteUserResponse> =
        ApiResult.success(DeleteUserResponse.from(userUseCase.delete(DeleteUserRequest.from(id).toCommand())))
}

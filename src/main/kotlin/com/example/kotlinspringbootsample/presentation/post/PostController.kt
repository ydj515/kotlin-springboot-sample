package com.example.kotlinspringbootsample.presentation.post

import com.example.kotlinspringbootsample.application.post.PostUseCase
import com.example.kotlinspringbootsample.application.post.command.CreatePostCommand
import com.example.kotlinspringbootsample.application.post.command.DeletePostCommand
import com.example.kotlinspringbootsample.application.post.command.FindPostsCommand
import com.example.kotlinspringbootsample.application.post.command.GetPostCommand
import com.example.kotlinspringbootsample.application.post.command.UpdatePostCommand
import com.example.kotlinspringbootsample.application.post.result.PostDeletedResult
import com.example.kotlinspringbootsample.application.post.result.PostResult
import com.example.kotlinspringbootsample.config.SwaggerRefs.POST_CREATE_REQUEST_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.POST_CREATE_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.POST_DELETE_REQUEST_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.POST_DELETE_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.POST_DETAIL_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.POST_LIST_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.POST_UPDATE_REQUEST_EXAMPLE_REF
import com.example.kotlinspringbootsample.config.SwaggerRefs.POST_UPDATE_SUCCESS_EXAMPLE_REF
import com.example.kotlinspringbootsample.presentation.common.ApiResult
import com.example.kotlinspringbootsample.presentation.common.ResultCode
import com.example.kotlinspringbootsample.presentation.post.request.PostDeleteRequest
import com.example.kotlinspringbootsample.presentation.post.request.PostRequest
import com.example.kotlinspringbootsample.presentation.post.response.PostDeletedResponse
import com.example.kotlinspringbootsample.presentation.post.response.PostResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody as SwaggerRequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.data.domain.Page
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/posts")
@Tag(name = "Posts", description = "게시글 조회와 생성/수정/삭제 API")
class PostController(
    private val postUseCase: PostUseCase
) {
    @Operation(summary = "게시글 목록 조회", description = "페이지 단위로 게시글 목록을 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "게시글 목록 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = POST_LIST_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @GetMapping
    fun getPosts(
        @Parameter(description = "페이지 번호", example = "0")
        @RequestParam(defaultValue = "0") page: Int,
        @Parameter(description = "페이지 크기", example = "10")
        @RequestParam(defaultValue = "10") size: Int
    ): ApiResult.Success<Page<PostResponse>> =
        ApiResult.success(
            postUseCase.getAllPosts(FindPostsCommand(page = page, size = size))
                .map(PostResult::toResponse)
        )

    @Operation(summary = "게시글 단건 조회", description = "게시글 ID로 상세 내용을 조회합니다.")
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "게시글 상세 조회 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = POST_DETAIL_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @GetMapping("/{id}")
    fun getPostById(
        @Parameter(description = "게시글 ID", example = "1")
        @PathVariable id: Long
    ): ApiResult.Success<PostResponse> =
        ApiResult.success(postUseCase.getPostById(GetPostCommand(id)).toResponse())

    @Operation(summary = "게시글 생성", description = "새 게시글을 생성합니다.")
    @SwaggerRequestBody(
        required = true,
        description = "게시글 생성 요청 바디",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = PostRequest::class),
            examples = [ExampleObject(ref = POST_CREATE_REQUEST_EXAMPLE_REF)]
        )]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "게시글 생성 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = POST_CREATE_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @ResponseStatus(HttpStatus.CREATED)
    @PostMapping
    fun createPost(@RequestBody postRequest: PostRequest): ApiResult.Success<PostResponse> =
        ApiResult.success(postUseCase.createPost(postRequest.toCreateCommand()).toResponse(), ResultCode.CREATED)

    @Operation(summary = "게시글 수정", description = "작성자 정보가 일치하는 게시글을 수정합니다.")
    @SwaggerRequestBody(
        required = true,
        description = "게시글 수정 요청 바디",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = PostRequest::class),
            examples = [ExampleObject(ref = POST_UPDATE_REQUEST_EXAMPLE_REF)]
        )]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "게시글 수정 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = POST_UPDATE_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @PutMapping("/{id}")
    fun updatePost(
        @Parameter(description = "게시글 ID", example = "1")
        @PathVariable id: Long,
        @RequestBody postRequest: PostRequest
    ): ApiResult.Success<PostResponse> =
        ApiResult.success(postUseCase.updatePost(postRequest.toUpdateCommand(id)).toResponse())

    @Operation(summary = "게시글 삭제", description = "작성자 정보가 일치하는 게시글을 삭제합니다.")
    @SwaggerRequestBody(
        required = true,
        description = "게시글 삭제 요청 바디",
        content = [Content(
            mediaType = "application/json",
            schema = Schema(implementation = PostDeleteRequest::class),
            examples = [ExampleObject(ref = POST_DELETE_REQUEST_EXAMPLE_REF)]
        )]
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "게시글 삭제 성공",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(ref = POST_DELETE_SUCCESS_EXAMPLE_REF)]
                )]
            )
        ]
    )
    @DeleteMapping("/{id}")
    fun deletePost(
        @Parameter(description = "게시글 ID", example = "1")
        @PathVariable id: Long,
        @RequestBody postRequest: PostDeleteRequest
    ): ApiResult.Success<PostDeletedResponse> =
        ApiResult.success(postUseCase.deletePost(postRequest.toDeleteCommand(id)).toResponse())
}

private fun PostRequest.toCreateCommand(): CreatePostCommand =
    CreatePostCommand(
        title = title,
        content = content,
        username = username,
        password = password
    )

private fun PostRequest.toUpdateCommand(id: Long): UpdatePostCommand =
    UpdatePostCommand(
        id = id,
        title = title,
        content = content,
        username = username,
        password = password
    )

private fun PostDeleteRequest.toDeleteCommand(id: Long): DeletePostCommand =
    DeletePostCommand(
        id = id,
        username = username,
        password = password
    )

private fun PostResult.toResponse(): PostResponse =
    PostResponse(
        title = title,
        content = content,
        username = username
    )

private fun PostDeletedResult.toResponse(): PostDeletedResponse =
    PostDeletedResponse(message = message)

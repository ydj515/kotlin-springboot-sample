package com.example.kotlinspringbootsample.config

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.examples.Example
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Content
import io.swagger.v3.oas.models.media.MediaType
import io.swagger.v3.oas.models.parameters.RequestBody
import io.swagger.v3.oas.models.responses.ApiResponse
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springdoc.core.models.GroupedOpenApi
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class SwaggerConfig {
    private val objectMapper = ObjectMapper().findAndRegisterModules()

    @Bean
    fun customOpenAPI(): OpenAPI =
        OpenAPI()
            .info(
                Info()
                    .title("API Documentation")
                    .version("1.0.0")
                    .description("kotlin springboot sample project입니다.")
            )
            .servers(
                listOf(
                    Server()
                        .url("/")
                        .description("Default server")
                )
            )

    @Bean
    fun publicApi(): GroupedOpenApi =
        GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/api/**", "/signup")
            .addOpenApiCustomizer(publicApiCustomizer())
            .build()

    @Bean
    fun publicApiCustomizer(): OpenApiCustomizer =
        OpenApiCustomizer { openApi ->
            registerComponents(openApi)
            customizeOperations(openApi)
        }

    private fun registerComponents(openApi: OpenAPI) {
        val components = openApi.components ?: Components()
        components.registerExamples()
        components.addResponses(
            SwaggerRefs.BAD_REQUEST_RESPONSE_NAME,
            response(
                description = "잘못된 요청",
                "invalidRequest" to SwaggerRefs.INVALID_REQUEST_FAILURE_EXAMPLE_REF,
                "signupValidation" to SwaggerRefs.SIGNUP_VALIDATION_FAILURE_EXAMPLE_REF,
                "invalidOrderItem" to SwaggerRefs.INVALID_ORDER_ITEM_FAILURE_EXAMPLE_REF
            )
        )
        components.addResponses(
            SwaggerRefs.NOT_FOUND_RESPONSE_NAME,
            response(
                description = "리소스를 찾을 수 없음",
                "orderNotFound" to SwaggerRefs.ORDER_NOT_FOUND_FAILURE_EXAMPLE_REF
            )
        )
        components.addResponses(
            SwaggerRefs.CONFLICT_RESPONSE_NAME,
            response(
                description = "상태 충돌 또는 중복 요청",
                "userAlreadyExists" to SwaggerRefs.SIGNUP_ALREADY_EXISTS_FAILURE_EXAMPLE_REF,
                "invalidOrderTransition" to SwaggerRefs.ORDER_CONFLICT_FAILURE_EXAMPLE_REF,
                "optimisticLockingConflict" to SwaggerRefs.OPTIMISTIC_LOCK_FAILURE_EXAMPLE_REF
            )
        )
        openApi.components(components)
    }

    private fun customizeOperations(openApi: OpenAPI) {
        openApi.customizePost("/signup") {
            requestBody = requestBody.withExamples(
                description = "회원가입 요청 바디",
                "request" to SwaggerRefs.SIGNUP_REQUEST_EXAMPLE_REF
            )
            setSuccessResponse("201", "회원가입 성공", "success" to SwaggerRefs.SIGNUP_SUCCESS_EXAMPLE_REF)
            setResponseRef("400", SwaggerRefs.BAD_REQUEST_RESPONSE_REF)
            setResponseRef("409", SwaggerRefs.CONFLICT_RESPONSE_REF)
        }

        openApi.customizeGet("/api/orders/status-summary") {
            setSuccessResponse(
                "200",
                "주문 상태 요약 조회 성공",
                "success" to SwaggerRefs.ORDER_STATUS_SUMMARY_SUCCESS_EXAMPLE_REF
            )
            setResponseRef("400", SwaggerRefs.BAD_REQUEST_RESPONSE_REF)
            setParameterExample("buyerUsername", "alice")
            setParameterExample("status", "PAID")
        }

        openApi.customizeGet("/api/orders") {
            setSuccessResponse("200", "주문 목록 조회 성공", "success" to SwaggerRefs.ORDER_LIST_SUCCESS_EXAMPLE_REF)
            setResponseRef("400", SwaggerRefs.BAD_REQUEST_RESPONSE_REF)
            setParameterExample("page", 0)
            setParameterExample("size", 10)
            setParameterExample("buyerUsername", "alice")
            setParameterExample("status", "PAID")
            setParameterExample("searchMode", "JPQL")
        }

        openApi.customizePost("/api/orders") {
            requestBody = requestBody.withExamples(
                description = "주문 생성 요청 바디",
                "request" to SwaggerRefs.ORDER_CREATE_REQUEST_EXAMPLE_REF
            )
            setSuccessResponse("201", "주문 생성 성공", "success" to SwaggerRefs.ORDER_CREATE_SUCCESS_EXAMPLE_REF)
            setResponseRef("400", SwaggerRefs.BAD_REQUEST_RESPONSE_REF)
        }

        openApi.customizeGet("/api/orders/{id}") {
            setSuccessResponse("200", "주문 상세 조회 성공", "success" to SwaggerRefs.ORDER_DETAIL_SUCCESS_EXAMPLE_REF)
            setResponseRef("404", SwaggerRefs.NOT_FOUND_RESPONSE_REF)
            setParameterExample("id", 1)
        }

        openApi.customizePost("/api/orders/{id}/pay") {
            setSuccessResponse("200", "주문 결제 성공", "success" to SwaggerRefs.ORDER_PAY_SUCCESS_EXAMPLE_REF)
            setResponseRef("404", SwaggerRefs.NOT_FOUND_RESPONSE_REF)
            setResponseRef("409", SwaggerRefs.CONFLICT_RESPONSE_REF)
            setParameterExample("id", 2)
        }

        openApi.customizePost("/api/orders/{id}/ship") {
            setSuccessResponse("200", "주문 배송 성공", "success" to SwaggerRefs.ORDER_SHIP_SUCCESS_EXAMPLE_REF)
            setResponseRef("404", SwaggerRefs.NOT_FOUND_RESPONSE_REF)
            setResponseRef("409", SwaggerRefs.CONFLICT_RESPONSE_REF)
            setParameterExample("id", 2)
        }

        openApi.customizePost("/api/orders/{id}/cancel") {
            setSuccessResponse("200", "주문 취소 성공", "success" to SwaggerRefs.ORDER_CANCEL_SUCCESS_EXAMPLE_REF)
            setResponseRef("404", SwaggerRefs.NOT_FOUND_RESPONSE_REF)
            setResponseRef("409", SwaggerRefs.CONFLICT_RESPONSE_REF)
            setParameterExample("id", 2)
        }
    }

    private fun Components.registerExamples() {
        addExamples(SwaggerRefs.SIGNUP_REQUEST_EXAMPLE_NAME, example("회원가입 요청", SIGNUP_REQUEST_EXAMPLE))
        addExamples(SwaggerRefs.SIGNUP_SUCCESS_EXAMPLE_NAME, example("회원가입 성공", SIGNUP_SUCCESS_EXAMPLE))
        addExamples(
            SwaggerRefs.SIGNUP_VALIDATION_FAILURE_EXAMPLE_NAME,
            example("회원가입 유효성 실패", SIGNUP_VALIDATION_FAILURE_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.SIGNUP_ALREADY_EXISTS_FAILURE_EXAMPLE_NAME,
            example("이미 존재하는 사용자", SIGNUP_ALREADY_EXISTS_FAILURE_EXAMPLE)
        )

        addExamples(
            SwaggerRefs.ORDER_CREATE_REQUEST_EXAMPLE_NAME,
            example("주문 생성 요청", ORDER_CREATE_REQUEST_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.ORDER_STATUS_SUMMARY_SUCCESS_EXAMPLE_NAME,
            example("주문 상태 요약 조회 성공", ORDER_STATUS_SUMMARY_SUCCESS_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.ORDER_LIST_SUCCESS_EXAMPLE_NAME,
            example("주문 목록 조회 성공", ORDER_LIST_SUCCESS_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.ORDER_DETAIL_SUCCESS_EXAMPLE_NAME,
            example("주문 상세 조회 성공", ORDER_DETAIL_SUCCESS_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.ORDER_CREATE_SUCCESS_EXAMPLE_NAME,
            example("주문 생성 성공", ORDER_CREATE_SUCCESS_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.ORDER_PAY_SUCCESS_EXAMPLE_NAME,
            example("주문 결제 성공", ORDER_PAY_SUCCESS_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.ORDER_SHIP_SUCCESS_EXAMPLE_NAME,
            example("주문 배송 성공", ORDER_SHIP_SUCCESS_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.ORDER_CANCEL_SUCCESS_EXAMPLE_NAME,
            example("주문 취소 성공", ORDER_CANCEL_SUCCESS_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.INVALID_REQUEST_FAILURE_EXAMPLE_NAME,
            example("쿼리 파라미터 변환 실패", INVALID_REQUEST_FAILURE_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.INVALID_ORDER_ITEM_FAILURE_EXAMPLE_NAME,
            example("잘못된 주문 항목", INVALID_ORDER_ITEM_FAILURE_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.ORDER_NOT_FOUND_FAILURE_EXAMPLE_NAME,
            example("주문을 찾을 수 없음", ORDER_NOT_FOUND_FAILURE_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.ORDER_CONFLICT_FAILURE_EXAMPLE_NAME,
            example("허용되지 않는 주문 상태 전이", ORDER_CONFLICT_FAILURE_EXAMPLE)
        )
        addExamples(
            SwaggerRefs.OPTIMISTIC_LOCK_FAILURE_EXAMPLE_NAME,
            example("동시 수정 충돌", OPTIMISTIC_LOCK_FAILURE_EXAMPLE)
        )
    }

    private fun Operation.setSuccessResponse(code: String, description: String, vararg examples: Pair<String, String>) {
        responses.remove("default")
        responses.remove(code)
        responses.addApiResponse(
            code,
            responseWithExamples(description = description, examples = examples)
        )
    }

    private fun Operation.setResponseRef(code: String, ref: String) {
        responses.remove("default")
        responses.remove(code)
        responses.addApiResponse(code, ApiResponse().apply { `$ref`(ref) })
    }

    private fun Operation.setParameterExample(name: String, example: Any) {
        parameters?.firstOrNull { it.name == name }?.example(example)
    }

    private fun OpenAPI.customizeGet(path: String, block: Operation.() -> Unit) {
        val pathItem = paths?.get(path) ?: return
        pathItem.get?.apply(block)
    }

    private fun OpenAPI.customizePost(path: String, block: Operation.() -> Unit) {
        val pathItem = paths?.get(path) ?: return
        pathItem.post?.apply(block)
    }

    private fun RequestBody?.withExamples(description: String, vararg examples: Pair<String, String>): RequestBody {
        val requestBody = this ?: RequestBody()
        val schema = requestBody.content?.get("application/json")?.schema
        requestBody.description = description
        requestBody.required = true
        requestBody.content = Content().addMediaType("application/json", mediaTypeWithExamples(schema, *examples))
        return requestBody
    }

    private fun responseWithExamples(description: String, examples: Array<out Pair<String, String>>): ApiResponse =
        ApiResponse()
            .description(description)
            .content(Content().addMediaType("application/json", mediaTypeWithExamples(null, *examples)))

    private fun mediaTypeWithExamples(
        schema: io.swagger.v3.oas.models.media.Schema<*>?,
        vararg examples: Pair<String, String>
    ): MediaType =
        MediaType().apply {
            schema?.let { schema(it) }
            examples.forEach { (name, ref) ->
                addExamples(name, refExample(ref))
            }
        }

    private fun example(summary: String, json: String): Example =
        Example()
            .summary(summary)
            .value(objectMapper.readTree(json))

    private fun response(description: String, vararg examples: Pair<String, String>): ApiResponse =
        ApiResponse()
            .description(description)
            .content(
                Content().addMediaType(
                    "application/json",
                    MediaType().apply {
                        examples.forEach { (name, ref) ->
                            addExamples(name, refExample(ref))
                        }
                    }
                )
            )

    private fun refExample(ref: String): Example =
        Example().apply {
            `$ref`(ref)
        }
}

private const val SIGNUP_REQUEST_EXAMPLE =
    """{"username":"user1","password":"password1"}"""

private const val SIGNUP_SUCCESS_EXAMPLE =
    """{"result":"success","code":"201","message":"Created","timestamp":"2026-05-08T13:30:00","data":{"username":"user1"}}"""

private const val SIGNUP_VALIDATION_FAILURE_EXAMPLE =
    """{"result":"failure","code":"400","message":"Invalid Request","timestamp":"2026-05-08T13:30:00","errors":{"username":"Username must be between 4 and 10 characters","password":"Password must be between 8 and 15 characters"}}"""

private const val SIGNUP_ALREADY_EXISTS_FAILURE_EXAMPLE =
    """{"result":"failure","code":"409","message":"user already exists","timestamp":"2026-05-08T13:30:00","errors":null}"""

private const val ORDER_CREATE_REQUEST_EXAMPLE =
    """{"buyerUsername":"alice","shippingAddress":{"recipient":"Alice Kim","zipCode":"06236","address1":"Seoul Gangnam-daero 123","address2":"10F"},"items":[{"productName":"Mechanical Keyboard","quantity":1,"unitPrice":129000.00},{"productName":"Palm Rest","quantity":1,"unitPrice":25000.00}]}"""

private const val ORDER_STATUS_SUMMARY_SUCCESS_EXAMPLE =
    """{"result":"success","code":"200","message":"Success","timestamp":"2026-05-08T13:00:00","data":[{"status":"PAID","count":2},{"status":"SHIPPED","count":1}]}"""

private const val ORDER_LIST_SUCCESS_EXAMPLE =
    """{"result":"success","code":"200","message":"Success","timestamp":"2026-05-08T13:00:00","data":{"content":[{"id":2,"version":1,"buyerUsername":"alice","status":"PAID","totalAmount":154000.00,"paidAt":"2026-05-08T12:05:00","shippedAt":null,"cancelledAt":null,"createdAt":"2026-05-08T11:00:00"}],"pageable":{"pageNumber":0,"pageSize":10},"totalElements":1,"totalPages":1}}"""

private const val ORDER_DETAIL_SUCCESS_EXAMPLE =
    """{"result":"success","code":"200","message":"Success","timestamp":"2026-05-08T13:00:00","data":{"id":2,"version":1,"buyerUsername":"alice","status":"PAID","recipient":"Alice Kim","zipCode":"06236","address1":"Seoul Gangnam-daero 123","address2":"10F","totalAmount":154000.00,"items":[{"productName":"Mechanical Keyboard","quantity":1,"unitPrice":129000.00,"lineAmount":129000.00},{"productName":"Palm Rest","quantity":1,"unitPrice":25000.00,"lineAmount":25000.00}],"paidAt":"2026-05-08T12:05:00","shippedAt":null,"cancelledAt":null,"createdAt":"2026-05-08T11:00:00"}}"""

private const val ORDER_CREATE_SUCCESS_EXAMPLE =
    """{"result":"success","code":"201","message":"Created","timestamp":"2026-05-08T13:00:00","data":{"id":5,"version":0,"buyerUsername":"alice","status":"CREATED","recipient":"Alice Kim","zipCode":"06236","address1":"Seoul Gangnam-daero 123","address2":"10F","totalAmount":154000.00,"items":[{"productName":"Mechanical Keyboard","quantity":1,"unitPrice":129000.00,"lineAmount":129000.00},{"productName":"Palm Rest","quantity":1,"unitPrice":25000.00,"lineAmount":25000.00}],"paidAt":null,"shippedAt":null,"cancelledAt":null,"createdAt":"2026-05-08T13:00:00"}}"""

private const val ORDER_PAY_SUCCESS_EXAMPLE =
    """{"result":"success","code":"200","message":"Success","timestamp":"2026-05-08T13:00:00","data":{"id":2,"version":2,"buyerUsername":"alice","status":"PAID","recipient":"Alice Kim","zipCode":"06236","address1":"Seoul Gangnam-daero 123","address2":"10F","totalAmount":154000.00,"items":[{"productName":"Mechanical Keyboard","quantity":1,"unitPrice":129000.00,"lineAmount":129000.00},{"productName":"Palm Rest","quantity":1,"unitPrice":25000.00,"lineAmount":25000.00}],"paidAt":"2026-05-08T12:05:00","shippedAt":null,"cancelledAt":null,"createdAt":"2026-05-08T11:00:00"}}"""

private const val ORDER_SHIP_SUCCESS_EXAMPLE =
    """{"result":"success","code":"200","message":"Success","timestamp":"2026-05-08T13:00:00","data":{"id":2,"version":3,"buyerUsername":"alice","status":"SHIPPED","recipient":"Alice Kim","zipCode":"06236","address1":"Seoul Gangnam-daero 123","address2":"10F","totalAmount":154000.00,"items":[{"productName":"Mechanical Keyboard","quantity":1,"unitPrice":129000.00,"lineAmount":129000.00},{"productName":"Palm Rest","quantity":1,"unitPrice":25000.00,"lineAmount":25000.00}],"paidAt":"2026-05-08T12:05:00","shippedAt":"2026-05-08T12:30:00","cancelledAt":null,"createdAt":"2026-05-08T11:00:00"}}"""

private const val ORDER_CANCEL_SUCCESS_EXAMPLE =
    """{"result":"success","code":"200","message":"Success","timestamp":"2026-05-08T13:00:00","data":{"id":2,"version":2,"buyerUsername":"alice","status":"CANCELLED","recipient":"Alice Kim","zipCode":"06236","address1":"Seoul Gangnam-daero 123","address2":"10F","totalAmount":154000.00,"items":[{"productName":"Mechanical Keyboard","quantity":1,"unitPrice":129000.00,"lineAmount":129000.00},{"productName":"Palm Rest","quantity":1,"unitPrice":25000.00,"lineAmount":25000.00}],"paidAt":"2026-05-08T12:05:00","shippedAt":null,"cancelledAt":"2026-05-08T12:10:00","createdAt":"2026-05-08T11:00:00"}}"""

private const val INVALID_REQUEST_FAILURE_EXAMPLE =
    """{"result":"failure","code":"400","message":"Invalid Request","timestamp":"2026-05-08T13:00:00","errors":{"status":"Failed to convert value to OrderStatus"}}"""

private const val INVALID_ORDER_ITEM_FAILURE_EXAMPLE =
    """{"result":"failure","code":"400","message":"unit price must be greater than zero","timestamp":"2026-05-08T13:00:00","errors":null}"""

private const val ORDER_NOT_FOUND_FAILURE_EXAMPLE =
    """{"result":"failure","code":"404","message":"Order not found with id 999","timestamp":"2026-05-08T13:00:00","errors":null}"""

private const val ORDER_CONFLICT_FAILURE_EXAMPLE =
    """{"result":"failure","code":"409","message":"only created or paid orders can be cancelled. current status: SHIPPED","timestamp":"2026-05-08T13:00:00","errors":null}"""

private const val OPTIMISTIC_LOCK_FAILURE_EXAMPLE =
    """{"result":"failure","code":"409","message":"order was modified concurrently. retry the request","timestamp":"2026-05-08T13:00:00","errors":null}"""

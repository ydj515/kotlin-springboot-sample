# Kotlin/JPA Cross-Sample Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Kotlin/JPA 샘플을 공통 아키텍처 규약에 맞춰 정렬하고, `domain/{도메인}/service`, `POST /api/users`, `POST /api/auth/login`, 공통 에러 응답, MDC 추적을 실제 코드로 반영한다.

**Architecture:** `presentation -> application -> domain -> infrastructure` 경계는 유지하되, repository를 사용하는 도메인 행위는 `domain/{도메인}/service`로 이동한다. 로그인은 controller/usecase 기반으로 재정렬하고, 기존 JWT 토큰 검증 필터는 유지하되 로그인 진입은 필터가 아니라 `AuthController`가 담당한다.

**Tech Stack:** Kotlin 1.9, Spring Boot 3.5, Spring Data JPA, Spring Security, JJWT, Kotest, MockK

---

### Task 1: 공통 실패 응답과 도메인 예외 추상화 정렬

**Files:**
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/common/error/BusinessException.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/presentation/common/ApiResult.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/presentation/common/ResultCode.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/presentation/common/GlobalExceptionHandler.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/domain/user/exception/UserAlreadyException.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/domain/user/exception/UserException.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/domain/order/exception/OrderNotFoundException.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/domain/order/exception/InvalidOrderItemException.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/domain/order/exception/InvalidOrderStatusTransitionException.kt`
- Test: `src/test/kotlin/com/example/kotlinspringbootsample/presentation/common/GlobalExceptionHandlerTest.kt`

- [ ] **Step 1: 실패 응답 contract를 고정하는 웹 레이어 테스트를 먼저 추가한다**

```kotlin
@WebMvcTest(
    controllers = [GlobalExceptionHandlerTest.FailureTestController::class],
    excludeAutoConfiguration = [
        UserDetailsServiceAutoConfiguration::class,
        SecurityAutoConfiguration::class
    ]
)
@Import(GlobalExceptionHandler::class)
class GlobalExceptionHandlerTest(
    @Autowired private val mockMvc: MockMvc
) : DescribeSpec({
    describe("GET /test/failure") {
        it("business exception을 status, code, message, path, traceId, timestamp 형식으로 변환한다") {
            mockMvc.get("/test/failure") {
                header("X-Trace-Id", "trace-test-001")
            }.andExpect {
                status { isConflict() }
                jsonPath("$.result") { value("failure") }
                jsonPath("$.status") { value(409) }
                jsonPath("$.code") { value("USER_ALREADY_EXISTS") }
                jsonPath("$.message") { value("user already exists") }
                jsonPath("$.path") { value("/test/failure") }
                jsonPath("$.traceId") { value("trace-test-001") }
                jsonPath("$.timestamp") { exists() }
            }
        }
    }
}) {
    @RestController
    private class FailureTestController {
        @GetMapping("/test/failure")
        fun failure(): Nothing = throw UserAlreadyException()
    }
}
```

- [ ] **Step 2: 새 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests com.example.kotlinspringbootsample.presentation.common.GlobalExceptionHandlerTest -i`

Expected: `FAILURE` with missing `status`, `path`, or `traceId` fields in the JSON body.

- [ ] **Step 3: 공통 예외 추상화와 실패 응답 필드를 구현한다**

```kotlin
package com.example.kotlinspringbootsample.common.error

import org.springframework.http.HttpStatus

abstract class BusinessException(
    val status: HttpStatus,
    val errorCode: String,
    override val message: String
) : RuntimeException(message)
```

```kotlin
sealed interface ApiResult<out T> {
    val result: String
    val code: String
    val message: String
    val timestamp: LocalDateTime

    data class Failure(
        override val code: String,
        override val message: String,
        val status: Int,
        val path: String,
        val traceId: String?,
        val errors: Any? = null,
        override val timestamp: LocalDateTime = LocalDateTime.now()
    ) : ApiResult<Nothing> {
        override val result: String = "failure"
    }

    companion object {
        fun failure(
            resultCode: ResultCode,
            status: Int = resultCode.status.value(),
            path: String,
            traceId: String?,
            message: String = resultCode.message,
            errors: Any? = null
        ): Failure = Failure(
            code = resultCode.code,
            message = message,
            status = status,
            path = path,
            traceId = traceId,
            errors = errors
        )
    }
}
```

```kotlin
enum class ResultCode(
    val status: HttpStatus,
    val code: String,
    val message: String
) {
    SUCCESS(HttpStatus.OK, "200", "Success"),
    CREATED(HttpStatus.CREATED, "201", "Created"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "400", "Invalid Request"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "404", "Not Found"),
    CONFLICT(HttpStatus.CONFLICT, "409", "Conflict"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "500", "Internal Server Error");

    companion object {
        fun from(status: HttpStatus): ResultCode =
            entries.firstOrNull { it.status == status } ?: INTERNAL_ERROR
    }
}
```

```kotlin
@RestControllerAdvice
class GlobalExceptionHandler {
    @ExceptionHandler(BusinessException::class)
    fun handleBusinessException(
        ex: BusinessException,
        request: HttpServletRequest
    ): ResponseEntity<ApiResult.Failure> =
        ResponseEntity.status(ex.status).body(
            ApiResult.failure(
                resultCode = ResultCode.from(ex.status),
                status = ex.status.value(),
                path = request.requestURI,
                traceId = TraceContext.getTraceId(),
                message = ex.message,
                errors = null
            ).copy(code = ex.errorCode)
        )
}
```

```kotlin
class UserAlreadyException : BusinessException(
    status = HttpStatus.CONFLICT,
    errorCode = "USER_ALREADY_EXISTS",
    message = "user already exists"
)
```

- [ ] **Step 4: 테스트와 기존 웹 테스트를 다시 실행해 contract를 확인한다**

Run: `./gradlew test --tests com.example.kotlinspringbootsample.presentation.common.GlobalExceptionHandlerTest --tests com.example.kotlinspringbootsample.presentation.order.OrderControllerTest --tests com.example.kotlinspringbootsample.presentation.user.UserControllerTest -i`

Expected: all selected tests `BUILD SUCCESSFUL`.

- [ ] **Step 5: 첫 번째 변경을 커밋한다**

```bash
git add src/main/kotlin/com/example/kotlinspringbootsample/common/error/BusinessException.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/presentation/common/ApiResult.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/presentation/common/ResultCode.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/presentation/common/GlobalExceptionHandler.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/domain/user/exception/UserAlreadyException.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/domain/user/exception/UserException.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/domain/order/exception/OrderNotFoundException.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/domain/order/exception/InvalidOrderItemException.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/domain/order/exception/InvalidOrderStatusTransitionException.kt \
  src/test/kotlin/com/example/kotlinspringbootsample/presentation/common/GlobalExceptionHandlerTest.kt
git commit -m "feat: align kotlin error contract with shared architecture"
```

### Task 2: MDC 추적과 비동기 컨텍스트 전파 추가

**Files:**
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/common/logging/TraceContext.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/config/logging/MdcLoggingFilter.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/config/async/MdcTaskDecorator.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/config/async/AsyncConfiguration.kt`
- Create: `src/main/resources/logback-spring.xml`
- Test: `src/test/kotlin/com/example/kotlinspringbootsample/config/logging/MdcAsyncPropagationTest.kt`
- Modify: `src/test/kotlin/com/example/kotlinspringbootsample/presentation/user/UserControllerTest.kt`

- [ ] **Step 1: MDC 전파와 응답 헤더를 검증하는 테스트를 추가한다**

```kotlin
class MdcAsyncPropagationTest : DescribeSpec({
    describe("MdcTaskDecorator") {
        it("호출 스레드의 traceId와 requestId를 작업 스레드로 복사한다") {
            val decorator = MdcTaskDecorator()
            MDC.put(TraceContext.TRACE_ID_KEY, "trace-async-001")
            MDC.put(TraceContext.REQUEST_ID_KEY, "request-async-001")

            var snapshot: Pair<String?, String?>? = null
            val decorated = decorator.decorate {
                snapshot = TraceContext.getTraceId() to TraceContext.getRequestId()
            }

            decorated.run()

            snapshot shouldBe ("trace-async-001" to "request-async-001")
            MDC.clear()
        }
    }
})
```

```kotlin
describe("POST /api/users") {
    it("X-Request-Id를 유지하고 X-Trace-Id를 응답 헤더에 추가한다") {
        every { userUseCase.registerMember(any()) } returns SignupResult(username = "alice")

        mockMvc.post("/api/users") {
            contentType = MediaType.APPLICATION_JSON
            header("X-Request-Id", "request-header-001")
            content = """{"username":"alice","password":"secret123"}"""
        }.andExpect {
            status { isCreated() }
            header { string("X-Request-Id", "request-header-001") }
            header { exists("X-Trace-Id") }
        }
    }
}
```

- [ ] **Step 2: 새 테스트가 실패하는지 확인한다**

Run: `./gradlew test --tests com.example.kotlinspringbootsample.config.logging.MdcAsyncPropagationTest --tests com.example.kotlinspringbootsample.presentation.user.UserControllerTest -i`

Expected: `FAILURE` because trace helper, filter, decorator, or `/api/users` path is not ready yet.

- [ ] **Step 3: TraceContext, MDC filter, async decorator, logback 구성을 추가한다**

```kotlin
object TraceContext {
    const val TRACE_ID_KEY = "traceId"
    const val REQUEST_ID_KEY = "requestId"
    const val HTTP_METHOD_KEY = "httpMethod"
    const val REQUEST_URI_KEY = "requestUri"
    const val CLIENT_IP_KEY = "clientIp"

    const val TRACE_ID_HEADER = "X-Trace-Id"
    const val REQUEST_ID_HEADER = "X-Request-Id"

    fun getTraceId(): String? = MDC.get(TRACE_ID_KEY)
    fun getRequestId(): String? = MDC.get(REQUEST_ID_KEY)
}
```

```kotlin
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcLoggingFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val traceId = request.getHeader(TraceContext.TRACE_ID_HEADER)?.takeIf(String::isNotBlank)
            ?: UUID.randomUUID().toString().replace("-", "")
        val requestId = request.getHeader(TraceContext.REQUEST_ID_HEADER)?.takeIf(String::isNotBlank) ?: traceId

        MDC.put(TraceContext.TRACE_ID_KEY, traceId)
        MDC.put(TraceContext.REQUEST_ID_KEY, requestId)
        MDC.put(TraceContext.HTTP_METHOD_KEY, request.method)
        MDC.put(TraceContext.REQUEST_URI_KEY, request.requestURI)
        MDC.put(TraceContext.CLIENT_IP_KEY, request.remoteAddr)

        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId)
        response.setHeader(TraceContext.REQUEST_ID_HEADER, requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.clear()
        }
    }
}
```

```kotlin
@Component
class MdcTaskDecorator : TaskDecorator {
    override fun decorate(runnable: Runnable): Runnable {
        val caller = MDC.getCopyOfContextMap()
        return Runnable {
            val previous = MDC.getCopyOfContextMap()
            try {
                if (caller.isNullOrEmpty()) MDC.clear() else MDC.setContextMap(caller)
                runnable.run()
            } finally {
                if (previous.isNullOrEmpty()) MDC.clear() else MDC.setContextMap(previous)
            }
        }
    }
}
```

- [ ] **Step 4: 테스트를 다시 실행해 MDC 규약을 확인한다**

Run: `./gradlew test --tests com.example.kotlinspringbootsample.config.logging.MdcAsyncPropagationTest --tests com.example.kotlinspringbootsample.presentation.user.UserControllerTest -i`

Expected: both tests `PASS`.

- [ ] **Step 5: 두 번째 변경을 커밋한다**

```bash
git add src/main/kotlin/com/example/kotlinspringbootsample/common/logging/TraceContext.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/config/logging/MdcLoggingFilter.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/config/async/MdcTaskDecorator.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/config/async/AsyncConfiguration.kt \
  src/main/resources/logback-spring.xml \
  src/test/kotlin/com/example/kotlinspringbootsample/config/logging/MdcAsyncPropagationTest.kt \
  src/test/kotlin/com/example/kotlinspringbootsample/presentation/user/UserControllerTest.kt
git commit -m "feat: add kotlin request tracing and mdc propagation"
```

### Task 3: domain service 도입과 user/order use case 슬림화

**Files:**
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/domain/user/service/UserRegistrationService.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/domain/user/service/UserLookupService.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/domain/order/service/OrderLookupService.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/domain/user/policy/UserRegistrationPolicy.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/application/user/UserUseCase.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/application/order/OrderUseCase.kt`
- Test: `src/test/kotlin/com/example/kotlinspringbootsample/domain/user/service/UserRegistrationServiceTest.kt`
- Test: `src/test/kotlin/com/example/kotlinspringbootsample/application/order/OrderUseCaseTest.kt`

- [ ] **Step 1: domain service 주도 구조를 고정하는 단위 테스트를 추가한다**

```kotlin
class UserRegistrationServiceTest : BehaviorSpec({
    val userRepository = mockk<UserRepository>()
    val policy = UserRegistrationPolicy()
    val service = UserRegistrationService(userRepository, policy)

    Given("회원가입 username이 이미 존재하면") {
        When("register를 호출하면") {
            Then("UserAlreadyException을 던진다") {
                every { userRepository.existsByUsername("alice") } returns true

                shouldThrow<UserAlreadyException> {
                    service.register(username = "alice", encodedPassword = "encoded-password")
                }
            }
        }
    }
})
```

```kotlin
Given("주문 결제 요청이 들어오면") {
    When("주문 조회는 lookup service를 통해 보장되면") {
        Then("use case는 상태 전이와 mapping만 수행한다") {
            every { orderLookupService.requireById(1L) } returns sampleOrder(1L)

            orderUseCase.payOrder(PayOrderCommand(1L))

            verify(exactly = 1) { orderLookupService.requireById(1L) }
        }
    }
}
```

- [ ] **Step 2: 테스트를 실행해 현재 구조와 어긋나는 부분을 확인한다**

Run: `./gradlew test --tests com.example.kotlinspringbootsample.domain.user.service.UserRegistrationServiceTest --tests com.example.kotlinspringbootsample.application.order.OrderUseCaseTest -i`

Expected: `FAILURE` because service classes and injected collaborators do not exist yet.

- [ ] **Step 3: policy는 순수 규칙으로 남기고, repository 의존은 service로 이동시킨다**

```kotlin
@Component
class UserRegistrationPolicy {
    fun validateUsername(username: String): String {
        val normalized = username.trim()
        if (normalized.isBlank()) {
            throw UserException("username is required")
        }
        return normalized
    }
}
```

```kotlin
@Component
class UserRegistrationService(
    private val userRepository: UserRepository,
    private val userRegistrationPolicy: UserRegistrationPolicy
) {
    fun register(username: String, encodedPassword: String): User {
        val normalizedUsername = userRegistrationPolicy.validateUsername(username)
        if (userRepository.existsByUsername(normalizedUsername)) {
            throw UserAlreadyException()
        }

        return userRepository.save(
            User(username = normalizedUsername, password = encodedPassword)
        )
    }
}
```

```kotlin
@Component
class UserLookupService(
    private val userRepository: UserRepository
) {
    fun requireByUsername(username: String): User =
        userRepository.findByUsername(username)
            ?: throw UserException("user not found with username $username")
}
```

```kotlin
@Component
class OrderLookupService(
    private val orderRepository: OrderRepository
) {
    fun requireById(id: Long): Order =
        orderRepository.findByIdAndDeletedAtIsNull(id)
            ?: throw OrderNotFoundException("Order not found with id $id")
}
```

```kotlin
@Service
class UserUseCase(
    private val userRegistrationService: UserRegistrationService,
    private val passwordEncoder: PasswordEncoder
) {
    fun registerMember(command: SignupCommand): SignupResult =
        userRegistrationService.register(
            username = command.username,
            encodedPassword = passwordEncoder.encode(command.password)
        ).toSignupResult()
}
```

- [ ] **Step 4: use case와 테스트를 새 collaborator 기준으로 맞춰 다시 실행한다**

Run: `./gradlew test --tests com.example.kotlinspringbootsample.domain.user.service.UserRegistrationServiceTest --tests com.example.kotlinspringbootsample.application.order.OrderUseCaseTest --tests com.example.kotlinspringbootsample.presentation.user.UserControllerTest -i`

Expected: selected tests `PASS`.

- [ ] **Step 5: 세 번째 변경을 커밋한다**

```bash
git add src/main/kotlin/com/example/kotlinspringbootsample/domain/user/service/UserRegistrationService.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/domain/user/service/UserLookupService.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/domain/order/service/OrderLookupService.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/domain/user/policy/UserRegistrationPolicy.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/application/user/UserUseCase.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/application/order/OrderUseCase.kt \
  src/test/kotlin/com/example/kotlinspringbootsample/domain/user/service/UserRegistrationServiceTest.kt \
  src/test/kotlin/com/example/kotlinspringbootsample/application/order/OrderUseCaseTest.kt
git commit -m "refactor: introduce kotlin domain services for user and order"
```

### Task 4: controller/usecase 기반 auth와 `/api/users` 경로 정렬

**Files:**
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/application/auth/command/LoginCommand.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/application/auth/result/LoginResult.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/presentation/auth/AuthController.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/presentation/auth/response/LoginResponse.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/DomainUserDetailsService.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/JwtTokenProvider.kt`
- Create: `src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/JwtAuthenticationFilter.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/application/auth/AuthUseCase.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/config/security/SecurityConfig.kt`
- Delete: `src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/TokenProvider.kt`
- Delete: `src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/AuthFilter.kt`
- Delete: `src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/CustomUsernamePasswordAuthenticationFilter.kt`
- Modify: `src/main/kotlin/com/example/kotlinspringbootsample/presentation/user/UserController.kt`
- Test: `src/test/kotlin/com/example/kotlinspringbootsample/presentation/auth/AuthControllerTest.kt`
- Modify: `src/test/kotlin/com/example/kotlinspringbootsample/presentation/user/UserControllerTest.kt`

- [ ] **Step 1: `/api/auth/login`과 `/api/users` contract를 고정하는 웹 테스트를 추가한다**

```kotlin
@WebMvcTest(
    controllers = [AuthController::class],
    excludeAutoConfiguration = [
        UserDetailsServiceAutoConfiguration::class,
        SecurityAutoConfiguration::class
    ]
)
class AuthControllerTest(
    @Autowired private val mockMvc: MockMvc,
    @MockkBean private val authUseCase: AuthUseCase
) : DescribeSpec({
    describe("POST /api/auth/login") {
        it("로그인 성공 시 Authorization 헤더와 accessToken payload를 반환한다") {
            every { authUseCase.login(LoginCommand("alice", "secret123")) } returns
                LoginResult(
                    username = "alice",
                    tokenType = "Bearer",
                    accessToken = "jwt-token",
                    accessTokenExpiresAt = 1893456000000
                )

            mockMvc.post("/api/auth/login") {
                contentType = MediaType.APPLICATION_JSON
                content = """{"username":"alice","password":"secret123"}"""
            }.andExpect {
                status { isOk() }
                header { string("Authorization", "Bearer jwt-token") }
                jsonPath("$.data.username") { value("alice") }
                jsonPath("$.data.accessToken") { value("jwt-token") }
            }
        }
    }
})
```

```kotlin
describe("POST /api/users") {
    it("회원가입 endpoint가 /api/users로 이동한다") {
        every { userUseCase.registerMember(any()) } returns SignupResult("alice")

        mockMvc.post("/api/users") {
            contentType = MediaType.APPLICATION_JSON
            content = """{"username":"alice","password":"secret123"}"""
        }.andExpect {
            status { isCreated() }
        }
    }
}
```

- [ ] **Step 2: 웹 테스트를 실행해 path/auth 흐름이 아직 구현되지 않았음을 확인한다**

Run: `./gradlew test --tests com.example.kotlinspringbootsample.presentation.auth.AuthControllerTest --tests com.example.kotlinspringbootsample.presentation.user.UserControllerTest -i`

Expected: `FAILURE` with 404 or missing bean errors.

- [ ] **Step 3: 로그인 유스케이스, controller, security adapter를 구현한다**

```kotlin
data class LoginCommand(
    val username: String,
    val password: String
)

data class LoginResult(
    val username: String,
    val tokenType: String,
    val accessToken: String,
    val accessTokenExpiresAt: Long
)
```

```kotlin
@Service
class AuthUseCase(
    private val authenticationManager: AuthenticationManager,
    private val jwtTokenProvider: JwtTokenProvider
) {
    fun login(command: LoginCommand): LoginResult {
        val authentication = authenticationManager.authenticate(
            UsernamePasswordAuthenticationToken(command.username, command.password)
        )
        return jwtTokenProvider.generateLoginResult(authentication)
    }
}
```

```kotlin
@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authUseCase: AuthUseCase
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, response: HttpServletResponse): ApiResult.Success<LoginResponse> {
        val result = authUseCase.login(LoginCommand(request.username, request.password))
        response.setHeader("Authorization", "${result.tokenType} ${result.accessToken}")
        return ApiResult.success(
            LoginResponse.from(result)
        )
    }
}
```

```kotlin
@Service
class DomainUserDetailsService(
    private val userLookupService: UserLookupService
) : UserDetailsService {
    override fun loadUserByUsername(username: String): UserDetails {
        val user = userLookupService.requireByUsername(username)
        return User(user.username, user.password, listOf(SimpleGrantedAuthority("ROLE_USER")))
    }
}
```

```kotlin
@PostMapping("/api/users")
@ResponseStatus(HttpStatus.CREATED)
fun signUp(@Valid @RequestBody signUpRequest: SignupRequest): ApiResult.Success<SignupResponse> =
    ApiResult.success(userUseCase.registerMember(signUpRequest.toCommand()).toResponse(), ResultCode.CREATED)
```

- [ ] **Step 4: 선택한 테스트와 auth 관련 기존 테스트를 다시 실행한다**

Run: `./gradlew test --tests com.example.kotlinspringbootsample.presentation.auth.AuthControllerTest --tests com.example.kotlinspringbootsample.presentation.user.UserControllerTest --tests com.example.kotlinspringbootsample.presentation.order.OrderControllerTest -i`

Expected: selected tests `PASS`.

- [ ] **Step 5: 네 번째 변경을 커밋한다**

```bash
git add src/main/kotlin/com/example/kotlinspringbootsample/application/auth/command/LoginCommand.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/application/auth/result/LoginResult.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/presentation/auth/AuthController.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/presentation/auth/response/LoginResponse.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/DomainUserDetailsService.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/JwtTokenProvider.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/JwtAuthenticationFilter.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/application/auth/AuthUseCase.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/config/security/SecurityConfig.kt \
  src/main/kotlin/com/example/kotlinspringbootsample/presentation/user/UserController.kt \
  src/test/kotlin/com/example/kotlinspringbootsample/presentation/auth/AuthControllerTest.kt \
  src/test/kotlin/com/example/kotlinspringbootsample/presentation/user/UserControllerTest.kt
git rm src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/TokenProvider.kt
git rm src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/AuthFilter.kt
git rm src/main/kotlin/com/example/kotlinspringbootsample/infrastructure/security/CustomUsernamePasswordAuthenticationFilter.kt
git commit -m "feat: move kotlin auth flow to controller usecase login"
```

### Task 5: 문서 동기화와 전체 검증

**Files:**
- Modify: `README.md`
- Modify: `docs/architecture-boundaries.md`
- Create: `docs/logging-and-tracing.md`
- Modify: `docs/error-handling.md`
- Modify: `docs/project-structure.md`
- Test: `src/test/kotlin/com/example/kotlinspringbootsample/KotlinSpringbootSampleApplicationTests.kt`

- [ ] **Step 1: 바뀐 구조와 경로를 반영하는 문서 변경 diff를 먼저 작성한다**

```markdown
- user 생성 endpoint를 `POST /api/users`로 설명한다.
- login endpoint를 `POST /api/auth/login`으로 설명한다.
- Kotlin/JPA도 `domain/{도메인}/service`를 기본 구조로 가진다고 명시한다.
- traceId, requestId, error response field를 문서 예시에 포함한다.
```

- [ ] **Step 2: 전체 테스트와 빌드를 실행해 남은 contract 깨짐을 수집한다**

Run: `./gradlew test build -i`

Expected: first run may fail if 문서/테스트/unused import 정리가 남아 있으면 그 목록이 출력된다.

- [ ] **Step 3: 남은 테스트와 문서를 정리하고 최종 상태를 맞춘다**

```markdown
## Architecture Boundaries

- `domain.user.service.UserRegistrationService`
  - username 중복 확인과 등록용 저장소 연동을 담당한다.
- `domain.order.service.OrderLookupService`
  - 주문 조회 보장과 not-found 예외 변환을 담당한다.
```

```kotlin
class KotlinSpringbootSampleApplicationTests {
    @Test
    fun contextLoads() {
    }
}
```

- [ ] **Step 4: 최종 검증을 다시 실행한다**

Run: `./gradlew test build -i`

Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: 마지막 변경을 커밋한다**

```bash
git add README.md docs/architecture-boundaries.md docs/logging-and-tracing.md docs/error-handling.md docs/project-structure.md src/test/kotlin/com/example/kotlinspringbootsample/KotlinSpringbootSampleApplicationTests.kt
git commit -m "docs: sync kotlin sample architecture guidance"
```

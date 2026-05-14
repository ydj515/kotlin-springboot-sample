package com.example.kotlinspringbootsample.config.logging

import com.example.kotlinspringbootsample.common.logging.TraceContext
import com.example.kotlinspringbootsample.config.async.MdcTaskDecorator
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import jakarta.servlet.FilterChain
import org.slf4j.MDC
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class MdcAsyncPropagationTest : DescribeSpec({

    val idPattern = Regex("^[A-Za-z0-9._-]{1,128}$")

    describe("MdcTaskDecorator") {
        it("호출 스레드의 traceId와 requestId를 작업 스레드로 복사한다") {
            val executor = ThreadPoolTaskExecutor().apply {
                corePoolSize = 1
                maxPoolSize = 1
                queueCapacity = 1
                setThreadNamePrefix("mdc-test-")
                setTaskDecorator(MdcTaskDecorator())
                initialize()
            }

            MDC.put(TraceContext.TRACE_ID_KEY, "trace-async-001")
            MDC.put(TraceContext.REQUEST_ID_KEY, "request-async-001")

            val capturedTraceId = AtomicReference<String?>()
            val capturedRequestId = AtomicReference<String?>()
            val latch = CountDownLatch(1)

            executor.execute {
                capturedTraceId.set(MDC.get(TraceContext.TRACE_ID_KEY))
                capturedRequestId.set(MDC.get(TraceContext.REQUEST_ID_KEY))
                latch.countDown()
            }

            latch.await(3, TimeUnit.SECONDS) shouldBe true
            capturedTraceId.get() shouldBe "trace-async-001"
            capturedRequestId.get() shouldBe "request-async-001"

            executor.shutdown()
            MDC.clear()
        }
    }

    describe("MdcLoggingFilter") {
        it("허용되지 않은 traceId와 requestId header는 새 값으로 대체한다") {
            val request = MockHttpServletRequest("POST", "/api/users").apply {
                addHeader(TraceContext.TRACE_ID_HEADER, "trace with space")
                addHeader(TraceContext.REQUEST_ID_HEADER, "request\npoison")
            }
            val response = MockHttpServletResponse()
            val filter = MdcLoggingFilter()

            val capturedTraceId = AtomicReference<String?>()
            val capturedRequestId = AtomicReference<String?>()

            filter.doFilter(
                request,
                response,
                FilterChain { _, _ ->
                    capturedTraceId.set(MDC.get(TraceContext.TRACE_ID_KEY))
                    capturedRequestId.set(MDC.get(TraceContext.REQUEST_ID_KEY))
                }
            )

            val responseTraceId = response.getHeader(TraceContext.TRACE_ID_HEADER)
            val responseRequestId = response.getHeader(TraceContext.REQUEST_ID_HEADER)

            responseTraceId shouldNotBe null
            responseRequestId shouldNotBe null
            responseTraceId!! shouldMatch idPattern
            responseRequestId!! shouldMatch idPattern
            responseTraceId shouldBe capturedTraceId.get()
            responseRequestId shouldBe capturedRequestId.get()
            responseTraceId shouldNotBe "trace with space"
            responseRequestId shouldNotBe "request\npoison"
        }

        it("필터 종료 후 기존 MDC 값을 복원한다") {
            MDC.put("existingKey", "existing-value")
            MDC.put(TraceContext.TRACE_ID_KEY, "original-trace-id")
            MDC.put(TraceContext.REQUEST_ID_KEY, "original-request-id")

            val request = MockHttpServletRequest("POST", "/api/users")
            val response = MockHttpServletResponse()
            val filter = MdcLoggingFilter()

            filter.doFilter(
                request,
                response,
                FilterChain { _, _ ->
                    MDC.get("existingKey") shouldBe "existing-value"
                    MDC.get(TraceContext.TRACE_ID_KEY) shouldNotBe "original-trace-id"
                    MDC.get(TraceContext.REQUEST_ID_KEY) shouldNotBe "original-request-id"
                }
            )

            MDC.get("existingKey") shouldBe "existing-value"
            MDC.get(TraceContext.TRACE_ID_KEY) shouldBe "original-trace-id"
            MDC.get(TraceContext.REQUEST_ID_KEY) shouldBe "original-request-id"
            MDC.clear()
        }
    }
})

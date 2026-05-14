package com.example.kotlinspringbootsample.config.logging

import com.example.kotlinspringbootsample.common.logging.TraceContext
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class MdcLoggingFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val previousContext = MDC.getCopyOfContextMap()
        val traceId = request.headerOrNull(TraceContext.TRACE_ID_HEADER) ?: UUID.randomUUID().toString()
        val requestId = request.headerOrNull(TraceContext.REQUEST_ID_HEADER) ?: UUID.randomUUID().toString()

        MDC.put(TraceContext.TRACE_ID_KEY, traceId)
        MDC.put(TraceContext.REQUEST_ID_KEY, requestId)

        request.setAttribute(TraceContext.TRACE_ID_KEY, traceId)
        request.setAttribute(TraceContext.REQUEST_ID_KEY, requestId)
        response.setHeader(TraceContext.TRACE_ID_HEADER, traceId)
        response.setHeader(TraceContext.REQUEST_ID_HEADER, requestId)

        try {
            filterChain.doFilter(request, response)
        } finally {
            restoreContext(previousContext)
        }
    }

    private fun HttpServletRequest.headerOrNull(name: String): String? =
        getHeader(name)
            ?.takeIf { it.isNotBlank() }
            ?.takeIf { VALID_TRACE_VALUE_PATTERN.matches(it) }

    private fun restoreContext(context: Map<String, String>?) {
        if (context == null) {
            MDC.clear()
            return
        }

        MDC.setContextMap(context)
    }

    companion object {
        private val VALID_TRACE_VALUE_PATTERN = Regex("^[A-Za-z0-9._-]{1,128}$")
    }
}

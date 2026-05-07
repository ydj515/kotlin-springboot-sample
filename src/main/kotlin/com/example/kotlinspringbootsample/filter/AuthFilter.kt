package com.example.kotlinspringbootsample.filter

import com.example.kotlinspringbootsample.auth.AuthConstants
import com.example.kotlinspringbootsample.config.security.TokenProvider
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class AuthFilter(private val tokenProvider: TokenProvider) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger(AuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        log.debug("Start authentication processing")
        resolveToken(request)
            ?.takeIf(tokenProvider::validateToken)
            ?.let(tokenProvider::getAuthentication)
            ?.let { SecurityContextHolder.getContext().authentication = it }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? =
        request.getHeader("Authorization")
            ?.takeIf { it.startsWith(AuthConstants.BEARER_PREFIX) }
            ?.removePrefix(AuthConstants.BEARER_PREFIX)
            ?.takeIf(String::isNotBlank)
}

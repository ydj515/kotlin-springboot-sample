package com.example.kotlinspringbootsample.infrastructure.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.web.filter.OncePerRequestFilter

class JwtAuthenticationFilter(
    private val jwtTokenProvider: JwtTokenProvider
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        resolveToken(request)
            ?.takeIf(jwtTokenProvider::validateToken)
            ?.let(jwtTokenProvider::getAuthentication)
            ?.let { authentication ->
                SecurityContextHolder.getContext().authentication = authentication
            }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? =
        request.getHeader("Authorization")
            ?.takeIf { it.startsWith(AuthConstants.BEARER_PREFIX) }
            ?.removePrefix(AuthConstants.BEARER_PREFIX)
            ?.takeIf(String::isNotBlank)
}

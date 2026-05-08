package com.example.kotlinspringbootsample.config.security

import com.example.kotlinspringbootsample.infrastructure.security.AuthFilter
import com.example.kotlinspringbootsample.infrastructure.security.CustomAuthenticationManager
import com.example.kotlinspringbootsample.infrastructure.security.CustomUsernamePasswordAuthenticationFilter
import com.example.kotlinspringbootsample.infrastructure.security.TokenProvider
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter

@Configuration
class SecurityConfig(
    private val tokenProvider: TokenProvider,
    private val objectMapper: ObjectMapper
) {

    private companion object {
        val PERMITTED_LIST = arrayOf(
            "/",
            "/login",
            "/logout",
            "/signup",
            "/swagger-ui/**",
            "/swagger-ui",
            "/swagger/**",
            "/v3/api-docs/**",
            "/api-docs/**",
            "/api-docs",
            "/h2-console/**",
            "/swagger-ui.html"
        )

        val API_WHITE_LIST = arrayOf(
            "/api/**"
        )
    }

    @Bean
    fun filterChain(
        http: HttpSecurity,
        customAuthenticationManager: CustomAuthenticationManager
    ): SecurityFilterChain {
        return http
            .csrf { it.disable() }
            .cors { }
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .headers { it.frameOptions { frameOptions -> frameOptions.disable() }.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests {
                it
                    .requestMatchers(*PERMITTED_LIST).permitAll()
                    .requestMatchers(*API_WHITE_LIST).permitAll()
            }
            .addFilter(
                CustomUsernamePasswordAuthenticationFilter(
                    customAuthenticationManager,
                    tokenProvider,
                    objectMapper
                )
            )
            .addFilterBefore(AuthFilter(tokenProvider), BasicAuthenticationFilter::class.java)
            .build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}

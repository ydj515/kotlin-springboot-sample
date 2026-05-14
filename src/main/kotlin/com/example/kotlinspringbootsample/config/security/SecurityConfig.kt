package com.example.kotlinspringbootsample.config.security

import com.example.kotlinspringbootsample.infrastructure.security.JwtAuthenticationFilter
import com.example.kotlinspringbootsample.infrastructure.security.JwtTokenProvider
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
class SecurityConfig(
    private val jwtTokenProvider: JwtTokenProvider
) {

    private companion object {
        val PERMITTED_LIST = arrayOf(
            "/",
            "/api/auth/login",
            "/api/users",
            "/swagger-ui/**",
            "/swagger-ui",
            "/swagger/**",
            "/v3/api-docs/**",
            "/api-docs/**",
            "/api-docs",
            "/h2-console/**",
            "/swagger-ui.html"
        )
    }

    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
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
                    .anyRequest().authenticated()
            }
            .addFilterBefore(
                JwtAuthenticationFilter(jwtTokenProvider),
                UsernamePasswordAuthenticationFilter::class.java
            )
            .build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}

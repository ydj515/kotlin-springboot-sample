package com.example.kotlinspringbootsample.config.security

import com.example.kotlinspringbootsample.filter.AuthFilter
import com.example.kotlinspringbootsample.filter.CustomUsernamePasswordAuthenticationFilter
import com.example.kotlinspringbootsample.user.service.UserService
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter

@Configuration
class SecurityConfig(
    private val tokenProvider: TokenProvider,
    private val customAuthenticationManager: CustomAuthenticationManager,
    private val userService: UserService,
    private val objectMapper: ObjectMapper
) {

    companion object {

        // 권한 제외 대상
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

        // api white list
        val API_WHITE_LIST = arrayOf(
            "/api/**"
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
            .authorizeHttpRequests { auth ->
                try {
                    auth
                        .requestMatchers(*PERMITTED_LIST).permitAll()
                        .requestMatchers(*API_WHITE_LIST).permitAll()
//                        .requestMatchers("/api/**").authenticated()
//                        .anyRequest().authenticated()
//                        .anyRequest().permitAll() // 로그인 하지 않고 모두 권한을 가짐
                } catch (e: Exception) {
//                    log.error(e.toString())
                }
            }
            .addFilter(getLoginAuthenticationFilter())
            .addFilterBefore(AuthFilter(tokenProvider), BasicAuthenticationFilter::class.java)
            .build()
    }

    private fun getLoginAuthenticationFilter(): CustomUsernamePasswordAuthenticationFilter {
        return CustomUsernamePasswordAuthenticationFilter(
            userService,
            customAuthenticationManager,
            tokenProvider,
            objectMapper
        )
    }
}
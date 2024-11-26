package com.example.kotlinspringbootsample.config.security

import com.example.kotlinspringbootsample.auth.AuthConstants
import com.example.kotlinspringbootsample.auth.dto.LoginResponse
import io.jsonwebtoken.*
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.PropertySource
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import java.security.Key
import java.util.*

@PropertySource("classpath:jwt.yml")
@Component
class TokenProvider(
    @Value("\${secret-key}") secretKey: String,
    @Value("\${access-token-expiration}") private val accessTokenExpiration: Long
) {
    private val key: Key

    init {
        val keyBytes = Decoders.BASE64.decode(secretKey)
        this.key = Keys.hmacShaKeyFor(keyBytes)
    }

    // 토큰 생성
    fun generateTokenDto(authentication: Authentication): LoginResponse {
        val authorities = authentication.authorities.joinToString(",") { it.authority }
        val now = Date()

        // Access Token 생성
        val accessToken = createAccessToken(authentication.name, authorities, now)

        // TODO : Refresh Token 생성 & 저장

        return LoginResponse(
            userId = authentication.name,
            type = AuthConstants.BEARER_PREFIX,
            accessToken = accessToken,
            refreshToken = null,
            accessTokenExpired = Date(now.time + accessTokenExpiration).time,
            refreshTokenExpired = null
        )
    }

    fun createAccessToken(subject: String, authorities: String, nowDate: Date): String {
        return Jwts.builder()
            .setSubject(subject)
            .claim(AuthConstants.AUTH_PREFIX, authorities)
            .setExpiration(Date(nowDate.time + accessTokenExpiration))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()
    }

    fun getAuthentication(accessToken: String): Authentication {
        val claims = parseClaims(accessToken)

        if (claims[AuthConstants.AUTH_PREFIX] == null) {
            throw RuntimeException()
        }

        val authorities = claims[AuthConstants.AUTH_PREFIX].toString()
            .split(",")
            .map { SimpleGrantedAuthority(it) }

        val principal = User(claims.subject, "", authorities)

        return UsernamePasswordAuthenticationToken(principal, "", authorities)
    }

    fun validateToken(token: String): Boolean {
        try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token)
        } catch (e: SecurityException) {
            throw AuthenticationCredentialsNotFoundException("잘못된 JWT 서명입니다.")
        } catch (e: MalformedJwtException) {
            throw AuthenticationCredentialsNotFoundException("잘못된 JWT 서명입니다.")
        } catch (e: ExpiredJwtException) {
            throw JwtException("만료된 JWT 토큰입니다.")
        } catch (e: UnsupportedJwtException) {
            throw UnsupportedJwtException("지원되지 않는 JWT 토큰입니다.")
        } catch (e: IllegalArgumentException) {
            throw IllegalArgumentException("JWT 토큰이 잘못되었습니다.")
        }

        return true
    }

    fun parseClaims(token: String): Claims {
        return try {
            Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).body
        } catch (e: ExpiredJwtException) {
            e.claims
        }
    }
}

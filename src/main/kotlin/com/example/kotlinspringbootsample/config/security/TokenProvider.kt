package com.example.kotlinspringbootsample.config.security

import com.example.kotlinspringbootsample.auth.AuthConstants
import com.example.kotlinspringbootsample.auth.dto.LoginResponse
import io.jsonwebtoken.*
import io.jsonwebtoken.io.Decoders
import io.jsonwebtoken.security.Keys
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.Authentication
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.User
import org.springframework.stereotype.Component
import java.security.Key
import java.time.Instant
import java.util.*

@Component
class TokenProvider(
    private val jwtProperties: JwtProperties
) {
    private val key: Key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secretKey))
    private val parser: JwtParser = Jwts.parserBuilder().setSigningKey(key).build()
    private val expirationMillis = jwtProperties.accessTokenExpiration

    fun generateTokenDto(authentication: Authentication): LoginResponse {
        val authorities = authentication.authorities.joinToString(",") { it.authority }
        val now = Instant.now()
        val accessToken = createAccessToken(authentication.name, authorities, now)

        return LoginResponse(
            userId = authentication.name,
            type = AuthConstants.BEARER_PREFIX,
            accessToken = accessToken,
            accessTokenExpired = now.toEpochMilli() + expirationMillis
        )
    }

    private fun createAccessToken(subject: String, authorities: String, now: Instant): String =
        Jwts.builder()
            .setSubject(subject)
            .claim(AuthConstants.AUTH_PREFIX, authorities)
            .setExpiration(Date(now.toEpochMilli() + expirationMillis))
            .signWith(key, SignatureAlgorithm.HS512)
            .compact()

    fun getAuthentication(accessToken: String): Authentication {
        val claims = parseClaims(accessToken)

        val authorities = claims[AuthConstants.AUTH_PREFIX]
            ?.toString()
            ?.split(",")
            ?.map(::SimpleGrantedAuthority)
            ?: throw AuthenticationCredentialsNotFoundException("권한 정보가 없는 JWT 토큰입니다.")

        val principal = User(claims.subject, "", authorities)

        return UsernamePasswordAuthenticationToken(principal, "", authorities)
    }

    fun validateToken(token: String): Boolean =
        try {
            parser.parseClaimsJws(token)
            true
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

    private fun parseClaims(token: String): Claims =
        try {
            parser.parseClaimsJws(token).body
        } catch (e: ExpiredJwtException) {
            e.claims
        }
}

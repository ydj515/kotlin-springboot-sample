package com.example.kotlinspringbootsample.infrastructure.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.ExpiredJwtException
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.JwtParser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.UnsupportedJwtException
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
import java.util.Date

@Component
class JwtTokenProvider(
    jwtProperties: JwtProperties
) {
    private val key: Key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(jwtProperties.secretKey))
    private val parser: JwtParser = Jwts.parserBuilder().setSigningKey(key).build()
    private val expirationMillis = jwtProperties.accessTokenExpiration

    fun issueAccessToken(authentication: Authentication): IssuedAccessToken {
        val authorities = authentication.authorities.joinToString(",") { it.authority }
        val now = Instant.now()

        return IssuedAccessToken(
            tokenType = AuthConstants.BEARER_PREFIX.trim(),
            accessToken = createAccessToken(authentication.name, authorities, now),
            accessTokenExpiresAt = now.toEpochMilli() + expirationMillis
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
            ?.filter(String::isNotBlank)
            ?.map(::SimpleGrantedAuthority)
            ?: throw AuthenticationCredentialsNotFoundException("권한 정보가 없는 JWT 토큰입니다.")

        val principal = User(claims.subject, "", authorities)

        return UsernamePasswordAuthenticationToken(principal, "", authorities)
    }

    fun validateToken(token: String): Boolean =
        try {
            parser.parseClaimsJws(token)
            true
        } catch (exception: SecurityException) {
            throw AuthenticationCredentialsNotFoundException("잘못된 JWT 서명입니다.")
        } catch (exception: MalformedJwtException) {
            throw AuthenticationCredentialsNotFoundException("잘못된 JWT 서명입니다.")
        } catch (exception: ExpiredJwtException) {
            throw JwtException("만료된 JWT 토큰입니다.")
        } catch (exception: UnsupportedJwtException) {
            throw UnsupportedJwtException("지원되지 않는 JWT 토큰입니다.")
        } catch (exception: IllegalArgumentException) {
            throw IllegalArgumentException("JWT 토큰이 잘못되었습니다.")
        }

    private fun parseClaims(token: String): Claims =
        try {
            parser.parseClaimsJws(token).body
        } catch (exception: ExpiredJwtException) {
            exception.claims
        }

    data class IssuedAccessToken(
        val tokenType: String,
        val accessToken: String,
        val accessTokenExpiresAt: Long
    )
}

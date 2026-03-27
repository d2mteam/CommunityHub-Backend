package com.app.communityhub.auth.security;

import com.app.communityhub.common.AppException;
import com.app.communityhub.config.AppProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class JwtService {

    private final AppProperties appProperties;

    public String generateAccessToken(AuthPrincipal user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(appProperties.getSecurity().getJwt().getAccessTokenTtl());
        return Jwts.builder()
                .issuer(appProperties.getSecurity().getJwt().getIssuer())
                .subject(user.id().toString())
                .claim("username", user.username())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey())
                .compact();
    }

    public String generateRefreshToken(UUID userId, String tokenId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(appProperties.getSecurity().getJwt().getRefreshTokenTtl());
        return Jwts.builder()
                .issuer(appProperties.getSecurity().getJwt().getIssuer())
                .subject(userId.toString())
                .id(tokenId)
                .claim("type", "refresh")
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(secretKey())
                .compact();
    }

    public TokenPrincipal parseAccessToken(String rawToken) {
        Claims claims = parseToken(rawToken).getPayload();
        return new TokenPrincipal(
                UUID.fromString(claims.getSubject()),
                claims.get("username", String.class),
                claims.getExpiration().toInstant(),
                null
        );
    }

    public TokenPrincipal parseRefreshToken(String rawToken) {
        Claims claims = parseToken(rawToken).getPayload();
        if (!"refresh".equals(claims.get("type", String.class))) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid refresh token");
        }
        return new TokenPrincipal(
                UUID.fromString(claims.getSubject()),
                null,
                claims.getExpiration().toInstant(),
                claims.getId()
        );
    }

    private Jws<Claims> parseToken(String rawToken) {
        try {
            return Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(rawToken);
        } catch (JwtException exception) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid token");
        }
    }

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(appProperties.getSecurity().getJwt().getSecret().getBytes(StandardCharsets.UTF_8));
    }

    public record TokenPrincipal(UUID userId, String username, Instant expiresAt, String tokenId) {
    }
}

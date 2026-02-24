package com.app.communityhub.auth;

import com.app.communityhub.auth.dto.AuthResponse;
import com.app.communityhub.auth.dto.LoginRequest;
import com.app.communityhub.auth.dto.RefreshRequest;
import com.app.communityhub.auth.dto.RegisterRequest;
import com.app.communityhub.auth.security.AuthUser;
import com.app.communityhub.auth.security.JwtService;
import com.app.communityhub.auth.security.UserDetailsLookupService;
import com.app.communityhub.common.AppException;
import com.app.communityhub.config.AppProperties;
import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.UserRepository;
import com.app.communityhub.user.UserService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserDetailsLookupService userDetailsLookupService;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties appProperties;
    private final UserService userService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new AppException(HttpStatus.CONFLICT, "Username is already taken");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        UserEntity saved = userRepository.save(user);
        return issueTokens(toAuthUser(saved));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        AuthUser authUser = userDetailsLookupService.loadByUsername(request.username().trim());
        if (!passwordEncoder.matches(request.password(), authUser.passwordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return issueTokens(authUser);
    }

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        JwtService.TokenPrincipal tokenPrincipal = jwtService.parseRefreshToken(request.refreshToken());
        RefreshTokenEntity storedToken = refreshTokenRepository.findByTokenId(tokenPrincipal.tokenId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Refresh token not found"));

        if (storedToken.isRevoked() || storedToken.getExpiresAt().isBefore(Instant.now())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        if (!storedToken.getTokenHash().equals(hashToken(request.refreshToken()))) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token mismatch");
        }

        storedToken.setRevoked(true);
        storedToken.setRevokedAt(Instant.now());

        AuthUser authUser = userDetailsLookupService.loadById(storedToken.getUser().getId());
        return issueTokens(authUser);
    }

    @Transactional
    public void logout(String refreshToken) {
        JwtService.TokenPrincipal tokenPrincipal = jwtService.parseRefreshToken(refreshToken);
        refreshTokenRepository.findByTokenId(tokenPrincipal.tokenId()).ifPresent(storedToken -> {
            storedToken.setRevoked(true);
            storedToken.setRevokedAt(Instant.now());
        });
    }

    private AuthResponse issueTokens(AuthUser authUser) {
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plus(appProperties.getSecurity().getJwt().getAccessTokenTtl());
        Instant refreshExpiresAt = now.plus(appProperties.getSecurity().getJwt().getRefreshTokenTtl());
        String accessToken = jwtService.generateAccessToken(authUser);
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = jwtService.generateRefreshToken(authUser.id(), tokenId);

        RefreshTokenEntity refreshTokenEntity = new RefreshTokenEntity();
        refreshTokenEntity.setTokenId(tokenId);
        refreshTokenEntity.setTokenHash(hashToken(refreshToken));
        refreshTokenEntity.setUser(userRepository.getReferenceById(authUser.id()));
        refreshTokenEntity.setExpiresAt(refreshExpiresAt);
        refreshTokenEntity.setRevoked(false);
        refreshTokenRepository.save(refreshTokenEntity);

        return new AuthResponse(
                accessToken,
                accessExpiresAt,
                refreshToken,
                refreshExpiresAt,
                userService.toProfileResponse(userRepository.getReferenceById(authUser.id()))
        );
    }

    private AuthUser toAuthUser(UserEntity user) {
        return AuthUser.builder()
                .id(user.getId())
                .username(user.getUsername())
                .passwordHash(user.getPasswordHash())
                .build();
    }

    private String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}

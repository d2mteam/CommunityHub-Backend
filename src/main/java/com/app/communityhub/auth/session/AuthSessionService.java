package com.app.communityhub.auth.session;

import com.app.communityhub.auth.api.AuthResponse;
import com.app.communityhub.auth.api.RefreshRequest;
import com.app.communityhub.auth.security.AuthPrincipal;
import com.app.communityhub.auth.security.JwtService;
import com.app.communityhub.common.AppException;
import com.app.communityhub.config.AppProperties;
import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.UserRepository;
import com.app.communityhub.user.profile.ProfileService;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthSessionService {

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final AppProperties appProperties;
    private final ProfileService profileService;

    @Transactional
    public AuthResponse refresh(RefreshRequest request) {
        JwtService.TokenPrincipal tokenPrincipal = jwtService.parseRefreshToken(request.refreshToken());
        RefreshTokenEntity storedToken = refreshTokenRepository.findByTokenId(tokenPrincipal.tokenId())
                .orElseThrow(() -> {
                    log.warn("Refresh rejected because token id was not found [tokenId={}]", tokenPrincipal.tokenId());
                    return new AppException(HttpStatus.UNAUTHORIZED, "Refresh token not found");
                });

        if (storedToken.isRevoked() || storedToken.getExpiresAt().isBefore(Instant.now())) {
            log.warn(
                    "Refresh rejected because token is revoked or expired [tokenId={}, userId={}, revoked={}]",
                    storedToken.getTokenId(),
                    storedToken.getUser().getId(),
                    storedToken.isRevoked()
            );
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token has expired");
        }

        if (!storedToken.getTokenHash().equals(TokenHashing.sha256(request.refreshToken()))) {
            log.warn(
                    "Refresh rejected because token hash mismatched [tokenId={}, userId={}]",
                    storedToken.getTokenId(),
                    storedToken.getUser().getId()
            );
            throw new AppException(HttpStatus.UNAUTHORIZED, "Refresh token mismatch");
        }

        storedToken.revoke(Instant.now());
        log.info("Refresh token rotated successfully [tokenId={}, userId={}]", storedToken.getTokenId(), storedToken.getUser().getId());
        return issueTokensForUser(storedToken.getUser());
    }

    @Transactional
    public void logout(String refreshToken) {
        JwtService.TokenPrincipal tokenPrincipal = jwtService.parseRefreshToken(refreshToken);
        refreshTokenRepository.findByTokenId(tokenPrincipal.tokenId()).ifPresent(storedToken -> {
            storedToken.revoke(Instant.now());
            log.info("Refresh token revoked during logout [tokenId={}, userId={}]", storedToken.getTokenId(), storedToken.getUser().getId());
        });
    }

    @Transactional
    public AuthResponse issueTokensForUser(UserEntity user) {
        AuthPrincipal authPrincipal = new AuthPrincipal(user.getId(), user.getUsername());
        Instant now = Instant.now();
        Instant accessExpiresAt = now.plus(appProperties.getSecurity().getJwt().getAccessTokenTtl());
        Instant refreshExpiresAt = now.plus(appProperties.getSecurity().getJwt().getRefreshTokenTtl());
        String accessToken = jwtService.generateAccessToken(authPrincipal);
        String tokenId = UUID.randomUUID().toString();
        String refreshToken = jwtService.generateRefreshToken(authPrincipal.id(), tokenId);

        RefreshTokenEntity refreshTokenEntity = RefreshTokenEntity.issue(
                tokenId,
                TokenHashing.sha256(refreshToken),
                userRepository.getReferenceById(authPrincipal.id()),
                refreshExpiresAt
        );
        refreshTokenRepository.save(refreshTokenEntity);
        log.info(
                "Issued access and refresh tokens [userId={}, username={}, tokenId={}]",
                authPrincipal.id(),
                authPrincipal.username(),
                tokenId
        );

        return new AuthResponse(
                accessToken,
                accessExpiresAt,
                refreshToken,
                refreshExpiresAt,
                profileService.toProfileResponse(user)
        );
    }
}

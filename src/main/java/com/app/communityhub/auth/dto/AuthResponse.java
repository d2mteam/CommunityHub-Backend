package com.app.communityhub.auth.dto;

import com.app.communityhub.user.dto.ProfileResponse;
import java.time.Instant;

public record AuthResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        ProfileResponse user
) {
}

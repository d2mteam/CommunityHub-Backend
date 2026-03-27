package com.app.communityhub.auth.api;

import com.app.communityhub.user.profile.dto.ProfileResponse;
import java.time.Instant;

public record AuthResponse(
        String accessToken,
        Instant accessTokenExpiresAt,
        String refreshToken,
        Instant refreshTokenExpiresAt,
        ProfileResponse user
) {
}

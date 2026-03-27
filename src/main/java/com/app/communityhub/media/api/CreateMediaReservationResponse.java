package com.app.communityhub.media.api;

import java.time.Instant;
import java.util.Map;

public record CreateMediaReservationResponse(
        String mediaKey,
        String uploadUrl,
        String method,
        Map<String, String> headers,
        Instant expiresAt
) {
}

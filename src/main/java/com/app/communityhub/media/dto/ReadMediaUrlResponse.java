package com.app.communityhub.media.dto;

import java.time.Instant;

public record ReadMediaUrlResponse(
        String mediaKey,
        String readUrl,
        Instant expiresAt
) {
}

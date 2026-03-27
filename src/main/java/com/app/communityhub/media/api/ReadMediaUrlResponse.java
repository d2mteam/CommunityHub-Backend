package com.app.communityhub.media.api;

import java.time.Instant;

public record ReadMediaUrlResponse(
        String mediaKey,
        String readUrl,
        Instant expiresAt
) {
}

package com.app.communityhub.user.dto;

import java.time.Instant;
import java.util.UUID;

public record ProfileResponse(
        UUID id,
        String username,
        MediaRefResponse avatar,
        Instant createdAt,
        Instant updatedAt
) {
}

package com.app.communityhub.content.dto;

import com.app.communityhub.user.dto.MediaRefResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PostResponse(
        UUID id,
        String content,
        AuthorSummaryResponse author,
        List<MediaRefResponse> attachments,
        Instant createdAt,
        Instant updatedAt
) {
}

package com.app.communityhub.content.dto;

import com.app.communityhub.user.dto.MediaRefResponse;
import java.time.Instant;
import java.util.UUID;

public record CommentResponse(
        UUID id,
        UUID parentId,
        UUID rootId,
        int depth,
        String content,
        AuthorSummaryResponse author,
        java.util.List<MediaRefResponse> attachments,
        Instant createdAt,
        Instant updatedAt,
        long replyCount
) {
}

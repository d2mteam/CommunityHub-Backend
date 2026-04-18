package com.app.communityhub.content.post;

import com.app.communityhub.content.shared.AttachmentResponse;
import com.app.communityhub.content.shared.AuthorSummaryResponse;
import java.time.Instant;
import java.util.List;

public record PostResponse(
        String id,
        String content,
        AuthorSummaryResponse author,
        List<AttachmentResponse> attachments,
        Instant createdAt,
        Instant updatedAt,
        Instant editedAt,
        boolean isEdited
) {
}

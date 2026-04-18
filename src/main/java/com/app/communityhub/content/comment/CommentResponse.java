package com.app.communityhub.content.comment;

import com.app.communityhub.content.shared.AttachmentResponse;
import com.app.communityhub.content.shared.AuthorSummaryResponse;
import java.time.Instant;
import java.util.List;

public record CommentResponse(
        String id,
        String parentId,
        String rootId,
        int depth,
        String content,
        AuthorSummaryResponse author,
        List<AttachmentResponse> attachments,
        Instant createdAt,
        Instant updatedAt,
        Instant editedAt,
        boolean isEdited,
        long replyCount
) {
}

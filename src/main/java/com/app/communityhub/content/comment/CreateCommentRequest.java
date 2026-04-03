package com.app.communityhub.content.comment;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateCommentRequest(
        @NotNull String postId,
        String parentId,
        String content,
        List<String> mediaKeys
) {
}

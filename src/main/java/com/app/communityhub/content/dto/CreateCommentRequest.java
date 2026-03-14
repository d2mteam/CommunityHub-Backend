package com.app.communityhub.content.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;

public record CreateCommentRequest(
        @NotNull UUID postId,
        UUID parentId,
        String content,
        List<String> mediaKeys
) {
}

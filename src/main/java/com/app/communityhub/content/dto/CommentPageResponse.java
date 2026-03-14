package com.app.communityhub.content.dto;

import java.util.List;

public record CommentPageResponse(
        List<CommentResponse> items,
        Integer nextOffset,
        boolean hasMore
) {
}

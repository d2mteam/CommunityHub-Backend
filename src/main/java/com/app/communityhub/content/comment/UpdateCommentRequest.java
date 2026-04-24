package com.app.communityhub.content.comment;

import java.util.List;

public record UpdateCommentRequest(
        String content,
        List<String> mediaKeys
) {
}

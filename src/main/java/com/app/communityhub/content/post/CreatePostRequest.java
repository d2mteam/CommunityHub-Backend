package com.app.communityhub.content.post;

import java.util.List;

public record CreatePostRequest(
        String content,
        List<String> mediaKeys
) {
}

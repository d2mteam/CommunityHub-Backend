package com.app.communityhub.content.dto;

import java.util.List;

public record CreatePostRequest(
        String content,
        List<String> mediaKeys
) {
}

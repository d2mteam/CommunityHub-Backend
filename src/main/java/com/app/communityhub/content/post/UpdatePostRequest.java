package com.app.communityhub.content.post;

import java.util.List;

public record UpdatePostRequest(
        String content,
        List<String> mediaKeys
) {
}

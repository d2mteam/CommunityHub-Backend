package com.app.communityhub.user.dto;

public record MediaRefResponse(
        String mediaKey,
        String mimeType,
        Integer width,
        Integer height
) {
}

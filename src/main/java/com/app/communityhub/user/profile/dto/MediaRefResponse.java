package com.app.communityhub.user.profile.dto;

public record MediaRefResponse(
        String mediaKey,
        String mimeType,
        Integer width,
        Integer height
) {
}

package com.app.communityhub.media.dto;

import com.app.communityhub.media.MediaStatus;

public record CompleteMediaResponse(
        String mediaKey,
        MediaStatus status,
        String mimeType,
        long sizeBytes,
        Integer width,
        Integer height
) {
}

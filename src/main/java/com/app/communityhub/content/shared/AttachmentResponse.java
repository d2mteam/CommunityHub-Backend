package com.app.communityhub.content.shared;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record AttachmentResponse(
        UUID attachmentId,
        String mediaKey,
        String type,
        String mimeType,
        Integer width,
        Integer height,
        int position,
        List<String> tags,
        Map<String, Object> metadata
) {
}

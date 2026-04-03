package com.app.communityhub.content.shared;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AttachmentDocument {

    private UUID attachmentId;
    private String mediaKey;
    private String type = "image";
    private String mimeType;
    private Integer width;
    private Integer height;
    private int position;
    private List<String> tags = new ArrayList<>();
    private Map<String, Object> metadata = new LinkedHashMap<>();

    @Builder
    private AttachmentDocument(
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
        this.attachmentId = attachmentId;
        this.mediaKey = mediaKey;
        this.type = type == null || type.isBlank() ? "image" : type;
        this.mimeType = mimeType;
        this.width = width;
        this.height = height;
        this.position = position;
        this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
        this.metadata = metadata == null ? new LinkedHashMap<>() : new LinkedHashMap<>(metadata);
    }
}

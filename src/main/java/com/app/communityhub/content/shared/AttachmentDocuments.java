package com.app.communityhub.content.shared;

import java.util.List;

public final class AttachmentDocuments {

    private AttachmentDocuments() {
    }

    public static List<AttachmentDocument> copyOf(List<AttachmentDocument> attachments) {
        if (attachments == null || attachments.isEmpty()) {
            return List.of();
        }
        return attachments.stream()
                .map(attachment -> AttachmentDocument.builder()
                        .attachmentId(attachment.getAttachmentId())
                        .mediaKey(attachment.getMediaKey())
                        .type(attachment.getType())
                        .mimeType(attachment.getMimeType())
                        .width(attachment.getWidth())
                        .height(attachment.getHeight())
                        .position(attachment.getPosition())
                        .tags(attachment.getTags())
                        .metadata(attachment.getMetadata())
                        .build())
                .toList();
    }
}

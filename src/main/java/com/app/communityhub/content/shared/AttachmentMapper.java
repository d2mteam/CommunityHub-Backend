package com.app.communityhub.content.shared;

import com.app.communityhub.media.MediaAssetEntity;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AttachmentMapper {

    AttachmentResponse toAttachmentResponse(AttachmentDocument attachment);

    default List<AttachmentDocument> toAttachmentDocuments(List<MediaAssetEntity> mediaAssets) {
        List<AttachmentDocument> attachments = new ArrayList<>();
        for (int index = 0; index < mediaAssets.size(); index++) {
            MediaAssetEntity mediaAsset = mediaAssets.get(index);
            attachments.add(AttachmentDocument.builder()
                    .attachmentId(UUID.randomUUID())
                    .mediaKey(mediaAsset.getMediaKey())
                    .type("image")
                    .mimeType(mediaAsset.getMimeType())
                    .width(mediaAsset.getWidth())
                    .height(mediaAsset.getHeight())
                    .position(index)
                    .tags(new ArrayList<>())
                    .metadata(new LinkedHashMap<>())
                    .build());
        }
        return attachments;
    }
}

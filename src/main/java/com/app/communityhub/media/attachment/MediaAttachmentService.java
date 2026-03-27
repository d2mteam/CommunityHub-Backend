package com.app.communityhub.media.attachment;

import com.app.communityhub.common.AppException;
import com.app.communityhub.config.AppProperties;
import com.app.communityhub.media.MediaAssetEntity;
import com.app.communityhub.media.MediaAssetRepository;
import com.app.communityhub.media.MediaStatus;
import com.app.communityhub.media.api.ReadMediaUrlResponse;
import com.app.communityhub.media.storage.ObjectStorageClient;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaAttachmentService {

    private final AppProperties appProperties;
    private final MediaAssetRepository mediaAssetRepository;
    private final ObjectStorageClient objectStorageClient;

    @Transactional(readOnly = true)
    public List<ReadMediaUrlResponse> resolveReadUrls(Collection<String> mediaKeys) {
        if (mediaKeys == null || mediaKeys.isEmpty()) {
            return List.of();
        }
        return mediaAssetRepository.findAllByMediaKeyIn(Set.copyOf(mediaKeys)).stream()
                .filter(mediaAsset -> mediaAsset.getStatus() == MediaStatus.ATTACHED)
                .sorted(Comparator.comparing(MediaAssetEntity::getCreatedAt))
                .map(mediaAsset -> {
                    ObjectStorageClient.PresignedRead read = objectStorageClient.createReadUrl(
                            mediaAsset.getObjectKey(),
                            appProperties.getMedia().getReadUrlTtl()
                    );
                    return new ReadMediaUrlResponse(mediaAsset.getMediaKey(), read.url(), read.expiresAt());
                })
                .toList();
    }

    @Transactional
    public MediaAssetEntity prepareAttachment(UUID userId, String mediaKey) {
        return getOwnedUploadedMedia(userId, mediaKey);
    }

    @Transactional
    public List<MediaAssetEntity> prepareAttachments(UUID userId, List<String> mediaKeys, int maxAttachments) {
        List<String> normalizedKeys = mediaKeys == null ? List.of() : mediaKeys.stream().distinct().toList();
        if (normalizedKeys.size() > maxAttachments) {
            throw new AppException(HttpStatus.BAD_REQUEST, "A maximum of %d images is allowed".formatted(maxAttachments));
        }
        List<MediaAssetEntity> attachments = new ArrayList<>();
        for (String mediaKey : normalizedKeys) {
            attachments.add(getOwnedUploadedMedia(userId, mediaKey));
        }
        return attachments;
    }

    @Transactional
    public void markAttached(Collection<MediaAssetEntity> mediaAssets) {
        Instant now = Instant.now();
        for (MediaAssetEntity mediaAsset : mediaAssets) {
            mediaAsset.markAttached(now);
        }
    }

    @Transactional
    public void markOrphaned(MediaAssetEntity mediaAsset) {
        mediaAsset.markOrphaned(Instant.now());
    }

    private MediaAssetEntity getOwnedUploadedMedia(UUID userId, String mediaKey) {
        MediaAssetEntity mediaAsset = getOwnedMedia(userId, mediaKey);
        if (mediaAsset.getStatus() != MediaStatus.UPLOADED) {
            throw new AppException(HttpStatus.CONFLICT, "Media must be uploaded before it can be attached");
        }
        return mediaAsset;
    }

    private MediaAssetEntity getOwnedMedia(UUID userId, String mediaKey) {
        MediaAssetEntity mediaAsset = mediaAssetRepository.findByMediaKey(mediaKey)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Media not found"));
        if (!mediaAsset.getOwnerUser().getId().equals(userId)) {
            throw new AppException(HttpStatus.FORBIDDEN, "Media does not belong to the current user");
        }
        return mediaAsset;
    }
}

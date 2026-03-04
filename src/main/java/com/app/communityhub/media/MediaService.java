package com.app.communityhub.media;

import com.app.communityhub.common.AppException;
import com.app.communityhub.config.AppProperties;
import com.app.communityhub.media.dto.CompleteMediaResponse;
import com.app.communityhub.media.dto.CreateMediaReservationRequest;
import com.app.communityhub.media.dto.CreateMediaReservationResponse;
import com.app.communityhub.media.dto.ReadMediaUrlResponse;
import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.UserRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private final AppProperties appProperties;
    private final MediaAssetRepository mediaAssetRepository;
    private final UserRepository userRepository;
    private final ObjectStorageClient objectStorageClient;

    @Transactional
    public CreateMediaReservationResponse reserve(UUID userId, CreateMediaReservationRequest request) {
        validateReservation(request);
        String mediaKey = UUID.randomUUID().toString();
        String objectKey = buildObjectKey(userId, mediaKey, request.fileName());

        MediaAssetEntity mediaAsset = new MediaAssetEntity();
        mediaAsset.setMediaKey(mediaKey);
        mediaAsset.setObjectKey(objectKey);
        mediaAsset.setOwnerUser(userRepository.getReferenceById(userId));
        mediaAsset.setStatus(MediaStatus.RESERVED);
        mediaAsset.setMimeType(request.mimeType());
        mediaAsset.setSizeBytes(request.sizeBytes());
        mediaAsset.setReservationExpiresAt(Instant.now().plus(appProperties.getMedia().getReservationTtl()));
        mediaAssetRepository.save(mediaAsset);

        ObjectStorageClient.PresignedUpload presignedUpload = objectStorageClient.createUploadUrl(
                objectKey,
                request.mimeType(),
                appProperties.getMedia().getUploadUrlTtl()
        );

        return new CreateMediaReservationResponse(
                mediaKey,
                presignedUpload.url(),
                presignedUpload.method(),
                presignedUpload.headers(),
                presignedUpload.expiresAt()
        );
    }

    @Transactional
    public CompleteMediaResponse complete(UUID userId, String mediaKey) {
        MediaAssetEntity mediaAsset = getOwnedMedia(userId, mediaKey);
        if (mediaAsset.getStatus() != MediaStatus.RESERVED) {
            throw new AppException(HttpStatus.CONFLICT, "Media is not awaiting upload completion");
        }

        ObjectStorageClient.ObjectInfo objectInfo = objectStorageClient.getObjectInfo(mediaAsset.getObjectKey());
        if (objectInfo.size() != mediaAsset.getSizeBytes()) {
            mediaAsset.setSizeBytes(objectInfo.size());
        }
        mediaAsset.setStatus(MediaStatus.UPLOADED);
        mediaAsset.setUploadedAt(Instant.now());
        mediaAsset.setEtag(objectInfo.eTag());
        mediaAsset.setMimeType(objectInfo.contentType() == null ? mediaAsset.getMimeType() : objectInfo.contentType());

        byte[] bytes = objectStorageClient.getObjectBytes(mediaAsset.getObjectKey());
        applyImageDimensions(mediaAsset, bytes);

        return new CompleteMediaResponse(
                mediaAsset.getMediaKey(),
                mediaAsset.getStatus(),
                mediaAsset.getMimeType(),
                mediaAsset.getSizeBytes(),
                mediaAsset.getWidth(),
                mediaAsset.getHeight()
        );
    }

    @Transactional(readOnly = true)
    public List<ReadMediaUrlResponse> resolveReadUrls(Collection<String> mediaKeys) {
        if (mediaKeys.isEmpty()) {
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
            mediaAsset.setStatus(MediaStatus.ATTACHED);
            mediaAsset.setAttachedAt(now);
            mediaAsset.setOrphanedAt(null);
        }
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void cleanupOrphanedMedia() {
        Instant cutoff = Instant.now().minus(appProperties.getMedia().getOrphanRetention());
        List<MediaAssetEntity> staleMedia = mediaAssetRepository.findAllByStatusInAndUpdatedAtBefore(
                List.of(MediaStatus.RESERVED, MediaStatus.UPLOADED, MediaStatus.ORPHANED),
                cutoff
        );
        for (MediaAssetEntity mediaAsset : staleMedia) {
            if (mediaAsset.getStatus() == MediaStatus.ATTACHED) {
                continue;
            }
            mediaAsset.setStatus(MediaStatus.ORPHANED);
            mediaAsset.setOrphanedAt(Instant.now());
            try {
                objectStorageClient.deleteObject(mediaAsset.getObjectKey());
            } catch (Exception exception) {
                log.warn("Could not delete orphaned object {}", mediaAsset.getObjectKey(), exception);
            }
        }
    }

    @Transactional
    public void markOrphaned(MediaAssetEntity mediaAsset) {
        mediaAsset.setStatus(MediaStatus.ORPHANED);
        mediaAsset.setOrphanedAt(Instant.now());
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

    private void validateReservation(CreateMediaReservationRequest request) {
        if (!appProperties.getMedia().getAllowedMimeTypes().contains(request.mimeType())) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Unsupported image type");
        }
        if (request.sizeBytes() > appProperties.getMedia().getMaxFileSizeBytes()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Image is too large");
        }
    }

    private void applyImageDimensions(MediaAssetEntity mediaAsset, byte[] bytes) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Invalid image payload");
            }
            mediaAsset.setWidth(image.getWidth());
            mediaAsset.setHeight(image.getHeight());
        } catch (IOException exception) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Could not inspect uploaded image");
        }
    }

    private String buildObjectKey(UUID userId, String mediaKey, String fileName) {
        String extension = extractExtension(fileName);
        return "media/" + userId + "/" + mediaKey + extension;
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return ".bin";
        }
        return fileName.substring(dotIndex).toLowerCase();
    }
}

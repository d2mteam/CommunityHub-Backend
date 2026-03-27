package com.app.communityhub.media.reservation;

import com.app.communityhub.common.AppException;
import com.app.communityhub.config.AppProperties;
import com.app.communityhub.media.MediaAssetEntity;
import com.app.communityhub.media.MediaAssetRepository;
import com.app.communityhub.media.MediaStatus;
import com.app.communityhub.media.api.CompleteMediaResponse;
import com.app.communityhub.media.api.CreateMediaReservationRequest;
import com.app.communityhub.media.api.CreateMediaReservationResponse;
import com.app.communityhub.media.storage.ObjectStorageClient;
import com.app.communityhub.user.UserRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaReservationService {

    private final AppProperties appProperties;
    private final MediaAssetRepository mediaAssetRepository;
    private final UserRepository userRepository;
    private final ObjectStorageClient objectStorageClient;
    private final ImageMetadataInspector imageMetadataInspector;

    @Transactional
    public CreateMediaReservationResponse reserve(UUID userId, CreateMediaReservationRequest request) {
        validateReservation(request);
        String mediaKey = UUID.randomUUID().toString();
        String objectKey = buildObjectKey(userId, mediaKey, request.fileName());

        MediaAssetEntity mediaAsset = MediaAssetEntity.builder()
                .mediaKey(mediaKey)
                .objectKey(objectKey)
                .ownerUser(userRepository.getReferenceById(userId))
                .mimeType(request.mimeType())
                .sizeBytes(request.sizeBytes())
                .reservationExpiresAt(Instant.now().plus(appProperties.getMedia().getReservationTtl()))
                .build();
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
        ImageMetadataInspector.Dimensions dimensions = imageMetadataInspector.inspect(
                objectStorageClient.getObjectBytes(mediaAsset.getObjectKey())
        );
        mediaAsset.completeUpload(
                objectInfo.size(),
                objectInfo.eTag(),
                objectInfo.contentType(),
                dimensions.width(),
                dimensions.height(),
                Instant.now()
        );

        return new CompleteMediaResponse(
                mediaAsset.getMediaKey(),
                mediaAsset.getStatus(),
                mediaAsset.getMimeType(),
                mediaAsset.getSizeBytes(),
                mediaAsset.getWidth(),
                mediaAsset.getHeight()
        );
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

    private String buildObjectKey(UUID userId, String mediaKey, String fileName) {
        return "media/" + userId + "/" + mediaKey + extractExtension(fileName);
    }

    private String extractExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return ".bin";
        }
        return fileName.substring(dotIndex).toLowerCase();
    }
}

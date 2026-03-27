package com.app.communityhub.media;

import com.app.communityhub.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "media_assets")
public class MediaAssetEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "media_key", nullable = false, unique = true, length = 100)
    private String mediaKey;

    @Column(name = "object_key", nullable = false, unique = true)
    private String objectKey;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_user_id", nullable = false)
    private UserEntity ownerUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private MediaStatus status;

    @Column(name = "mime_type", nullable = false, length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column(length = 100)
    private String etag;

    @Column(name = "reservation_expires_at", nullable = false)
    private Instant reservationExpiresAt;

    @Column(name = "uploaded_at")
    private Instant uploadedAt;

    @Column(name = "attached_at")
    private Instant attachedAt;

    @Column(name = "orphaned_at")
    private Instant orphanedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Builder
    private MediaAssetEntity(
            String mediaKey,
            String objectKey,
            UserEntity ownerUser,
            String mimeType,
            long sizeBytes,
            Instant reservationExpiresAt
    ) {
        this.mediaKey = mediaKey;
        this.objectKey = objectKey;
        this.ownerUser = ownerUser;
        this.status = MediaStatus.RESERVED;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.reservationExpiresAt = reservationExpiresAt;
    }

    public void completeUpload(
            long sizeBytes,
            String etag,
            String mimeType,
            Integer width,
            Integer height,
            Instant uploadedAt
    ) {
        this.sizeBytes = sizeBytes;
        this.status = MediaStatus.UPLOADED;
        this.uploadedAt = uploadedAt;
        this.etag = etag;
        this.mimeType = mimeType == null || mimeType.isBlank() ? this.mimeType : mimeType;
        this.width = width;
        this.height = height;
        this.orphanedAt = null;
    }

    public void markAttached(Instant attachedAt) {
        this.status = MediaStatus.ATTACHED;
        this.attachedAt = attachedAt;
        this.orphanedAt = null;
    }

    public void markOrphaned(Instant orphanedAt) {
        this.status = MediaStatus.ORPHANED;
        this.orphanedAt = orphanedAt;
    }
}

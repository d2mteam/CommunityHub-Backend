package com.app.communityhub.content.post;

import com.app.communityhub.content.shared.AttachmentDocument;
import com.app.communityhub.content.shared.AttachmentDocuments;
import com.app.communityhub.content.shared.ContentActionSource;
import com.app.communityhub.content.shared.ContentRevisionEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(
        name = "post_revisions",
        uniqueConstraints = @UniqueConstraint(name = "uk_post_revisions_entity_revision", columnNames = {"entity_id", "revision_number"})
)
public class PostRevisionEntity {

    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @Column(name = "entity_id", nullable = false)
    private Long entityId;

    @Column(name = "revision_number", nullable = false)
    private int revisionNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 20)
    private ContentRevisionEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_source", nullable = false, length = 20)
    private ContentActionSource actionSource;

    @Column(nullable = false, length = 5000)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments_jsonb", nullable = false, columnDefinition = "jsonb")
    private List<AttachmentDocument> attachments = new ArrayList<>();

    @Column(name = "actor_user_id", nullable = false)
    private UUID actorUserId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Builder
    private PostRevisionEntity(
            Long entityId,
            int revisionNumber,
            ContentRevisionEventType eventType,
            ContentActionSource actionSource,
            String content,
            List<AttachmentDocument> attachments,
            UUID actorUserId,
            Instant createdAt
    ) {
        this.entityId = entityId;
        this.revisionNumber = revisionNumber;
        this.eventType = eventType;
        this.actionSource = actionSource;
        this.content = content;
        this.attachments = new ArrayList<>(AttachmentDocuments.copyOf(attachments));
        this.actorUserId = actorUserId;
        this.createdAt = createdAt;
    }
}

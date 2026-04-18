package com.app.communityhub.content.comment;

import com.app.communityhub.content.post.PostEntity;
import com.app.communityhub.content.shared.AttachmentDocument;
import com.app.communityhub.content.shared.ContentActionSource;
import com.app.communityhub.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comments")
public class CommentEntity {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "post_id", nullable = false)
    private PostEntity post;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "author_id", nullable = false)
    private UserEntity author;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private CommentEntity parent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_id")
    private CommentEntity root;

    @Column(nullable = false)
    private int depth;

    @Column(nullable = false, length = 5000)
    private String content;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "attachments_jsonb", nullable = false, columnDefinition = "jsonb")
    private List<AttachmentDocument> attachments = new ArrayList<>();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "edited_at")
    private Instant editedAt;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_user_id")
    private UUID deletedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "deleted_source", length = 20)
    private ContentActionSource deletedSource;

    @Builder
    private CommentEntity(
            Long id,
            PostEntity post,
            UserEntity author,
            String content,
            List<AttachmentDocument> attachments
    ) {
        this.id = id;
        this.post = post;
        this.author = author;
        this.content = content;
        replaceAttachments(attachments);
    }

    public void markAsRoot() {
        this.parent = null;
        this.root = this;
        this.depth = 0;
    }

    public void attachToParent(CommentEntity parent) {
        this.parent = parent;
        this.root = parent.root == null ? parent : parent.root;
        this.depth = parent.depth + 1;
    }

    public void replaceAttachments(List<AttachmentDocument> attachments) {
        this.attachments = attachments == null ? new ArrayList<>() : new ArrayList<>(attachments);
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void markEdited(Instant editedAt) {
        this.editedAt = editedAt;
    }

    public void markDeleted(UUID actorUserId, ContentActionSource source, Instant deletedAt) {
        this.deletedAt = deletedAt;
        this.deletedByUserId = actorUserId;
        this.deletedSource = source;
    }
}

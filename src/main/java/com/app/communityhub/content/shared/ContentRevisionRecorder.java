package com.app.communityhub.content.shared;

import com.app.communityhub.content.comment.CommentEntity;
import com.app.communityhub.content.comment.CommentRevisionEntity;
import com.app.communityhub.content.comment.CommentRevisionRepository;
import com.app.communityhub.content.post.PostEntity;
import com.app.communityhub.content.post.PostRevisionEntity;
import com.app.communityhub.content.post.PostRevisionRepository;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentRevisionRecorder {

    private final PostRevisionRepository postRevisionRepository;
    private final CommentRevisionRepository commentRevisionRepository;

    public void recordPost(PostEntity post, ContentRevisionEventType eventType, ContentActionSource actionSource, UUID actorUserId) {
        postRevisionRepository.save(PostRevisionEntity.builder()
                .entityId(post.getId())
                .revisionNumber(postRevisionRepository.findMaxRevisionNumberByEntityId(post.getId()) + 1)
                .eventType(eventType)
                .actionSource(actionSource)
                .content(post.getContent())
                .attachments(post.getAttachments())
                .actorUserId(actorUserId)
                .createdAt(Instant.now())
                .build());
    }

    public void recordComment(
            CommentEntity comment,
            ContentRevisionEventType eventType,
            ContentActionSource actionSource,
            UUID actorUserId
    ) {
        commentRevisionRepository.save(CommentRevisionEntity.builder()
                .entityId(comment.getId())
                .postId(comment.getPost().getId())
                .parentId(comment.getParent() == null ? null : comment.getParent().getId())
                .rootId(comment.getRoot() == null ? null : comment.getRoot().getId())
                .depth(comment.getDepth())
                .revisionNumber(commentRevisionRepository.findMaxRevisionNumberByEntityId(comment.getId()) + 1)
                .eventType(eventType)
                .actionSource(actionSource)
                .content(comment.getContent())
                .attachments(comment.getAttachments())
                .actorUserId(actorUserId)
                .createdAt(Instant.now())
                .build());
    }
}

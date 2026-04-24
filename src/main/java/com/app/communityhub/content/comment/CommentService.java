package com.app.communityhub.content.comment;

import com.app.communityhub.auth.security.AuthPrincipal;
import com.app.communityhub.common.AppException;
import com.app.communityhub.common.SnowflakeIdGenerator;
import com.app.communityhub.config.AppProperties;
import com.app.communityhub.content.post.PostEntity;
import com.app.communityhub.content.post.PostRepository;
import com.app.communityhub.content.shared.AttachmentMapper;
import com.app.communityhub.content.shared.ContentActionSource;
import com.app.communityhub.content.shared.ContentAuthorizationPolicy;
import com.app.communityhub.content.shared.ContentIdParser;
import com.app.communityhub.content.shared.ContentRevisionEventType;
import com.app.communityhub.content.shared.ContentRevisionRecorder;
import com.app.communityhub.content.shared.ContentResponseAssembler;
import com.app.communityhub.content.shared.CursorCodec;
import com.app.communityhub.content.shared.CursorPageResponse;
import com.app.communityhub.content.shared.CursorToken;
import com.app.communityhub.content.shared.SortOrder;
import com.app.communityhub.media.MediaAssetEntity;
import com.app.communityhub.media.attachment.MediaAttachmentService;
import com.app.communityhub.user.UserRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final AppProperties appProperties;
    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final MediaAttachmentService mediaAttachmentService;
    private final AttachmentMapper attachmentMapper;
    private final ContentAuthorizationPolicy contentAuthorizationPolicy;
    private final ContentRevisionRecorder contentRevisionRecorder;
    private final ContentResponseAssembler contentResponseAssembler;
    private final CursorCodec cursorCodec;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public CommentResponse create(UUID authorId, CreateCommentRequest request) {
        String normalizedContent = normalizeContent(request.content());
        List<String> mediaKeys = request.mediaKeys() == null ? List.of() : request.mediaKeys();
        if (normalizedContent.isBlank() && mediaKeys.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Comment content or images are required");
        }

        Long postId = ContentIdParser.requireId(request.postId(), "postId");
        Long parentId = ContentIdParser.optionalId(request.parentId(), "parentId");

        PostEntity post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Post not found"));

        List<MediaAssetEntity> attachments = mediaAttachmentService.prepareAttachments(
                authorId,
                mediaKeys,
                appProperties.getContent().getComments().getMaxAttachments()
        );
        mediaAttachmentService.markAttached(attachments);
        CommentEntity comment = CommentEntity.builder()
                .id(snowflakeIdGenerator.nextId())
                .post(post)
                .author(userRepository.getReferenceById(authorId))
                .content(normalizedContent)
                .attachments(attachmentMapper.toAttachmentDocuments(attachments))
                .build();

        if (parentId != null) {
            CommentEntity parent = commentRepository.findByIdAndDeletedAtIsNull(parentId)
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Parent comment not found"));
            if (!parent.getPost().getId().equals(post.getId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Parent comment does not belong to the selected post");
            }
            comment.attachToParent(parent);
        } else {
            comment.markAsRoot();
        }
        CommentEntity saved = commentRepository.save(comment);
        contentRevisionRecorder.recordComment(saved, ContentRevisionEventType.CREATED, ContentActionSource.AUTHOR, authorId);
        return toSingleCommentResponse(saved);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<CommentResponse> getPage(Long postId, Long parentId, SortOrder sort, String cursor, int limit) {
        if (!postRepository.existsByIdAndDeletedAtIsNull(postId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Post not found");
        }

        if (parentId != null && !commentRepository.existsByIdAndPostIdAndDeletedAtIsNull(parentId, postId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Parent comment not found");
        }

        int pageSize = sanitizeLimit(limit);
        SortOrder effectiveSort = parentId == null ? sort : SortOrder.OLDEST;
        CursorToken cursorToken = cursorCodec.decode(cursor, effectiveSort, parentId);
        List<CommentEntity> page = fetchPage(postId, parentId, effectiveSort, cursorToken, pageSize + 1);
        boolean hasMore = page.size() > pageSize;
        List<CommentEntity> visibleItems = hasMore ? page.subList(0, pageSize) : page;

        Map<Long, Long> replyCounts = visibleItems.isEmpty()
                ? Collections.emptyMap()
                : commentRepository.countRepliesByParentIds(visibleItems.stream().map(CommentEntity::getId).toList()).stream()
                .collect(java.util.stream.Collectors.toMap(CommentReplyCountView::getParentId, CommentReplyCountView::getReplyCount));

        List<CommentResponse> items = contentResponseAssembler.toCommentResponses(visibleItems, replyCounts);
        String nextCursor = hasMore
                ? cursorCodec.encode(new CursorToken(
                        visibleItems.get(visibleItems.size() - 1).getId(),
                        effectiveSort,
                        parentId
                ))
                : null;
        return new CursorPageResponse<>(items, nextCursor, hasMore, effectiveSort);
    }

    @Transactional
    public CommentResponse update(AuthPrincipal actor, Long commentId, UpdateCommentRequest request) {
        CommentEntity comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Comment not found"));
        contentAuthorizationPolicy.requireCanEditComment(actor, comment);

        String normalizedContent = normalizeContent(request.content());
        List<String> normalizedKeys = request.mediaKeys() == null ? List.of() : request.mediaKeys().stream().distinct().toList();
        if (normalizedContent.isBlank() && normalizedKeys.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Comment content or images are required");
        }

        boolean contentChanged = !normalizedContent.equals(comment.getContent());
        List<String> currentKeys = comment.getAttachments().stream().map(attachment -> attachment.getMediaKey()).toList();
        boolean attachmentsChanged = !normalizedKeys.equals(currentKeys);
        if (!contentChanged && !attachmentsChanged) {
            return toSingleCommentResponse(comment);
        }

        if (attachmentsChanged) {
            List<MediaAssetEntity> attachments = mediaAttachmentService.prepareAttachmentsForUpdate(
                    actor.id(),
                    normalizedKeys,
                    appProperties.getContent().getComments().getMaxAttachments()
            );
            mediaAttachmentService.markAttached(attachments);
            comment.replaceAttachments(attachmentMapper.toAttachmentDocuments(attachments));
        }
        if (contentChanged) {
            comment.updateContent(normalizedContent);
        }
        comment.markEdited(Instant.now());
        contentRevisionRecorder.recordComment(comment, ContentRevisionEventType.UPDATED, ContentActionSource.AUTHOR, actor.id());
        return toSingleCommentResponse(comment);
    }

    @Transactional
    public void delete(AuthPrincipal actor, Long commentId) {
        CommentEntity comment = commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Comment not found"));
        contentAuthorizationPolicy.requireCanDeleteComment(actor, comment);

        Instant now = Instant.now();
        List<Long> subtreeIds = commentRepository.findVisibleSubtreeIds(commentId);
        List<CommentEntity> subtree = commentRepository.findAllById(subtreeIds).stream()
                .sorted(Comparator.comparing(CommentEntity::getId))
                .toList();
        for (CommentEntity node : subtree) {
            node.markDeleted(actor.id(), ContentActionSource.AUTHOR, now);
            contentRevisionRecorder.recordComment(node, ContentRevisionEventType.DELETED, ContentActionSource.AUTHOR, actor.id());
        }
    }

    private List<CommentEntity> fetchPage(Long postId, Long parentId, SortOrder sort, CursorToken cursorToken, int pageSize) {
        PageRequest pageRequest = PageRequest.of(0, pageSize);
        if (parentId != null) {
            return cursorToken == null
                    ? commentRepository.findReplyPage(postId, parentId, pageRequest)
                    : commentRepository.findReplyPageAfter(postId, parentId, cursorToken.id(), pageRequest);
        }

        return switch (sort) {
            case NEWEST -> cursorToken == null
                    ? commentRepository.findRootPageNewest(postId, pageRequest)
                    : commentRepository.findRootPageNewestAfter(postId, cursorToken.id(), pageRequest);
            case OLDEST -> cursorToken == null
                    ? commentRepository.findRootPageOldest(postId, pageRequest)
                    : commentRepository.findRootPageOldestAfter(postId, cursorToken.id(), pageRequest);
        };
    }

    private int sanitizeLimit(int limit) {
        int defaultPageSize = appProperties.getContent().getComments().getDefaultPageSize();
        int maxPageSize = appProperties.getContent().getComments().getMaxPageSize();
        if (limit <= 0) {
            return Math.min(defaultPageSize, maxPageSize);
        }
        return Math.min(limit, maxPageSize);
    }

    private CommentResponse toSingleCommentResponse(CommentEntity comment) {
        Map<Long, Long> replyCounts = commentRepository.countRepliesByParentIds(List.of(comment.getId())).stream()
                .collect(java.util.stream.Collectors.toMap(CommentReplyCountView::getParentId, CommentReplyCountView::getReplyCount));
        return contentResponseAssembler.toCommentResponses(List.of(comment), replyCounts).getFirst();
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }
}

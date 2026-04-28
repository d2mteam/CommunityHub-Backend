package com.app.communityhub.content.post;

import com.app.communityhub.auth.security.AuthPrincipal;
import com.app.communityhub.common.AppException;
import com.app.communityhub.common.SnowflakeIdGenerator;
import com.app.communityhub.config.AppProperties;
import com.app.communityhub.content.comment.CommentEntity;
import com.app.communityhub.content.comment.CommentRepository;
import com.app.communityhub.content.shared.AttachmentMapper;
import com.app.communityhub.content.shared.ContentActionSource;
import com.app.communityhub.content.shared.ContentAuthorizationPolicy;
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
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PostService {

    private final AppProperties appProperties;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final MediaAttachmentService mediaAttachmentService;
    private final AttachmentMapper attachmentMapper;
    private final ContentAuthorizationPolicy contentAuthorizationPolicy;
    private final ContentRevisionRecorder contentRevisionRecorder;
    private final ContentResponseAssembler contentResponseAssembler;
    private final CursorCodec cursorCodec;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Transactional
    public PostResponse create(UUID authorId, CreatePostRequest request) {
        String normalizedContent = normalizeContent(request.content());
        List<String> mediaKeys = request.mediaKeys() == null ? List.of() : request.mediaKeys();
        if (normalizedContent.isBlank() && mediaKeys.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Post content or images are required");
        }

        List<MediaAssetEntity> attachments = mediaAttachmentService.prepareAttachments(
                authorId,
                mediaKeys,
                appProperties.getContent().getPosts().getMaxAttachments()
        );
        mediaAttachmentService.markAttached(attachments);

        PostEntity post = PostEntity.builder()
                .id(snowflakeIdGenerator.nextId())
                .author(userRepository.getReferenceById(authorId))
                .content(normalizedContent)
                .attachments(attachmentMapper.toAttachmentDocuments(attachments))
                .build();

        PostEntity savedPost = postRepository.save(post);
        contentRevisionRecorder.recordPost(savedPost, ContentRevisionEventType.CREATED, ContentActionSource.AUTHOR, authorId);
        log.info(
                "Created post [postId={}, authorId={}, attachmentCount={}]",
                savedPost.getId(),
                authorId,
                savedPost.getAttachments().size()
        );
        return contentResponseAssembler.toPostResponse(savedPost);
    }

    @Transactional(readOnly = true)
    public CursorPageResponse<PostResponse> list(String cursor, SortOrder sort, int limit) {
        int pageSize = sanitizeLimit(limit);
        CursorToken cursorToken = cursorCodec.decode(cursor, sort, null);
        List<PostEntity> page = switch (sort) {
            case NEWEST -> cursorToken == null
                    ? postRepository.findPageNewest(PageRequest.of(0, pageSize + 1))
                    : postRepository.findPageNewestAfter(cursorToken.id(), PageRequest.of(0, pageSize + 1));
            case OLDEST -> cursorToken == null
                    ? postRepository.findPageOldest(PageRequest.of(0, pageSize + 1))
                    : postRepository.findPageOldestAfter(cursorToken.id(), PageRequest.of(0, pageSize + 1));
        };

        boolean hasMore = page.size() > pageSize;
        List<PostEntity> visibleItems = hasMore ? page.subList(0, pageSize) : page;
        String nextCursor = hasMore
                ? cursorCodec.encode(new CursorToken(
                        visibleItems.get(visibleItems.size() - 1).getId(),
                        sort,
                        null
                ))
                : null;

        List<PostResponse> items = visibleItems.stream()
                .map(contentResponseAssembler::toPostResponse)
                .toList();
        return new CursorPageResponse<>(items, nextCursor, hasMore, sort);
    }

    @Transactional(readOnly = true)
    public PostResponse get(Long postId) {
        PostEntity post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Post not found"));
        return contentResponseAssembler.toPostResponse(post);
    }

    @Transactional
    public PostResponse update(AuthPrincipal actor, Long postId, UpdatePostRequest request) {
        PostEntity post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Post not found"));
        contentAuthorizationPolicy.requireCanEditPost(actor, post);

        String normalizedContent = normalizeContent(request.content());
        List<String> normalizedKeys = request.mediaKeys() == null ? List.of() : request.mediaKeys().stream().distinct().toList();
        if (normalizedContent.isBlank() && normalizedKeys.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Post content or images are required");
        }

        boolean contentChanged = !normalizedContent.equals(post.getContent());
        List<String> currentKeys = post.getAttachments().stream().map(attachment -> attachment.getMediaKey()).toList();
        boolean attachmentsChanged = !normalizedKeys.equals(currentKeys);
        if (!contentChanged && !attachmentsChanged) {
            log.info("Skipped post update because no changes were detected [postId={}, actorId={}]", postId, actor.id());
            return contentResponseAssembler.toPostResponse(post);
        }

        if (attachmentsChanged) {
            List<MediaAssetEntity> attachments = mediaAttachmentService.prepareAttachmentsForUpdate(
                    actor.id(),
                    normalizedKeys,
                    appProperties.getContent().getPosts().getMaxAttachments()
            );
            mediaAttachmentService.markAttached(attachments);
            post.replaceAttachments(attachmentMapper.toAttachmentDocuments(attachments));
        }
        if (contentChanged) {
            post.updateContent(normalizedContent);
        }
        post.markEdited(Instant.now());
        contentRevisionRecorder.recordPost(post, ContentRevisionEventType.UPDATED, ContentActionSource.AUTHOR, actor.id());
        log.info(
                "Updated post [postId={}, actorId={}, contentChanged={}, attachmentsChanged={}, attachmentCount={}]",
                postId,
                actor.id(),
                contentChanged,
                attachmentsChanged,
                post.getAttachments().size()
        );
        return contentResponseAssembler.toPostResponse(post);
    }

    @Transactional
    public void delete(AuthPrincipal actor, Long postId) {
        PostEntity post = postRepository.findByIdAndDeletedAtIsNull(postId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Post not found"));
        contentAuthorizationPolicy.requireCanDeletePost(actor, post);

        Instant now = Instant.now();
        post.markDeleted(actor.id(), ContentActionSource.AUTHOR, now);
        List<CommentEntity> visibleComments = commentRepository.findAllByPostIdAndDeletedAtIsNullOrderByIdAsc(postId);
        for (CommentEntity comment : visibleComments) {
            comment.markDeleted(actor.id(), ContentActionSource.AUTHOR, now);
            contentRevisionRecorder.recordComment(comment, ContentRevisionEventType.DELETED, ContentActionSource.AUTHOR, actor.id());
        }
        contentRevisionRecorder.recordPost(post, ContentRevisionEventType.DELETED, ContentActionSource.AUTHOR, actor.id());
        log.info(
                "Soft deleted post and visible comments [postId={}, actorId={}, cascadedCommentCount={}]",
                postId,
                actor.id(),
                visibleComments.size()
        );
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }

    private int sanitizeLimit(int limit) {
        int defaultPageSize = appProperties.getContent().getPosts().getDefaultPageSize();
        int maxPageSize = appProperties.getContent().getPosts().getMaxPageSize();
        if (limit <= 0) {
            return Math.min(defaultPageSize, maxPageSize);
        }
        return Math.min(limit, maxPageSize);
    }
}

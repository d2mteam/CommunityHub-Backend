package com.app.communityhub.content.post;

import com.app.communityhub.common.AppException;
import com.app.communityhub.common.SnowflakeIdGenerator;
import com.app.communityhub.config.AppProperties;
import com.app.communityhub.content.shared.AttachmentMapper;
import com.app.communityhub.content.shared.ContentResponseAssembler;
import com.app.communityhub.content.shared.CursorCodec;
import com.app.communityhub.content.shared.CursorPageResponse;
import com.app.communityhub.content.shared.CursorToken;
import com.app.communityhub.content.shared.SortOrder;
import com.app.communityhub.media.MediaAssetEntity;
import com.app.communityhub.media.attachment.MediaAttachmentService;
import com.app.communityhub.user.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private final AppProperties appProperties;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final MediaAttachmentService mediaAttachmentService;
    private final AttachmentMapper attachmentMapper;
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

        return contentResponseAssembler.toPostResponse(postRepository.save(post));
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
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Post not found"));
        return contentResponseAssembler.toPostResponse(post);
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

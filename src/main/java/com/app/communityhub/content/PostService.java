package com.app.communityhub.content;

import com.app.communityhub.common.AppException;
import com.app.communityhub.content.dto.CreatePostRequest;
import com.app.communityhub.content.dto.PostResponse;
import com.app.communityhub.media.MediaAssetEntity;
import com.app.communityhub.media.MediaService;
import com.app.communityhub.user.UserRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PostService {

    private static final int MAX_ATTACHMENTS_PER_POST = 8;

    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final MediaService mediaService;
    private final ContentMapper contentMapper;

    @Transactional
    public PostResponse create(UUID authorId, CreatePostRequest request) {
        String normalizedContent = normalizeContent(request.content());
        List<String> mediaKeys = request.mediaKeys() == null ? List.of() : request.mediaKeys();
        if (normalizedContent.isBlank() && mediaKeys.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Post content or images are required");
        }

        PostEntity post = new PostEntity();
        post.setAuthor(userRepository.getReferenceById(authorId));
        post.setContent(normalizedContent);

        List<MediaAssetEntity> attachments = mediaService.prepareAttachments(authorId, mediaKeys, MAX_ATTACHMENTS_PER_POST);
        mediaService.markAttached(attachments);
        for (int index = 0; index < attachments.size(); index++) {
            PostAttachmentEntity attachment = new PostAttachmentEntity();
            attachment.setPost(post);
            attachment.setMediaAsset(attachments.get(index));
            attachment.setOrderIndex(index);
            post.getAttachments().add(attachment);
        }

        return contentMapper.toPostResponse(postRepository.save(post));
    }

    @Transactional(readOnly = true)
    public List<PostResponse> list() {
        return postRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(contentMapper::toPostResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PostResponse get(UUID postId) {
        PostEntity post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Post not found"));
        return contentMapper.toPostResponse(post);
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content.trim();
    }
}

package com.app.communityhub.content;

import com.app.communityhub.common.AppException;
import com.app.communityhub.content.dto.CommentPageResponse;
import com.app.communityhub.content.dto.CommentResponse;
import com.app.communityhub.content.dto.CreateCommentRequest;
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
public class CommentService {

    private static final int MAX_ATTACHMENTS_PER_COMMENT = 8;

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final MediaService mediaService;
    private final ContentMapper contentMapper;

    @Transactional
    public CommentResponse create(UUID authorId, CreateCommentRequest request) {
        String normalizedContent = request.content() == null ? "" : request.content().trim();
        List<String> mediaKeys = request.mediaKeys() == null ? List.of() : request.mediaKeys();
        if (normalizedContent.isBlank() && mediaKeys.isEmpty()) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Comment content or images are required");
        }

        PostEntity post = postRepository.findById(request.postId())
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Post not found"));

        CommentEntity comment = new CommentEntity();
        comment.setPost(post);
        comment.setAuthor(userRepository.getReferenceById(authorId));
        comment.setContent(normalizedContent);

        if (request.parentId() != null) {
            CommentEntity parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "Parent comment not found"));
            if (!parent.getPost().getId().equals(post.getId())) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Parent comment does not belong to the selected post");
            }
            comment.setParent(parent);
            comment.setRoot(parent.getRoot() == null ? parent : parent.getRoot());
            comment.setDepth(parent.getDepth() + 1);
        } else {
            comment.setDepth(0);
        }

        List<MediaAssetEntity> attachments = mediaService.prepareAttachments(authorId, mediaKeys, MAX_ATTACHMENTS_PER_COMMENT);
        mediaService.markAttached(attachments);
        for (int index = 0; index < attachments.size(); index++) {
            CommentAttachmentEntity attachment = new CommentAttachmentEntity();
            attachment.setComment(comment);
            attachment.setMediaAsset(attachments.get(index));
            attachment.setOrderIndex(index);
            comment.getAttachments().add(attachment);
        }

        CommentEntity saved = commentRepository.save(comment);
        if (saved.getParent() == null) {
            saved.setRoot(saved);
            saved = commentRepository.save(saved);
        }

        return contentMapper.toSingleComment(saved);
    }

    @Transactional(readOnly = true)
    public CommentPageResponse getPage(UUID postId, UUID parentId, int offset, int limit) {
        if (!postRepository.existsById(postId)) {
            throw new AppException(HttpStatus.NOT_FOUND, "Post not found");
        }
        List<CommentEntity> comments = parentId == null
                ? commentRepository.findAllByPostIdAndParentIsNullOrderByCreatedAtDesc(postId)
                : commentRepository.findAllByPostIdAndParentIdOrderByCreatedAtAsc(postId, parentId);
        return contentMapper.toCommentPage(comments, offset, limit, comment -> commentRepository.countByParentId(comment.getId()));
    }
}

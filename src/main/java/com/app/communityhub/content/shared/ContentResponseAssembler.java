package com.app.communityhub.content.shared;

import com.app.communityhub.content.comment.CommentEntity;
import com.app.communityhub.content.comment.CommentResponse;
import com.app.communityhub.content.post.PostEntity;
import com.app.communityhub.content.post.PostResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentResponseAssembler {

    private final ContentDtoMapper contentDtoMapper;

    public PostResponse toPostResponse(PostEntity post) {
        return contentDtoMapper.toPostResponse(post);
    }

    public List<CommentResponse> toCommentResponses(List<CommentEntity> comments, Map<Long, Long> replyCounts) {
        return comments.stream()
                .map(comment -> toCommentResponse(comment, replyCounts.getOrDefault(comment.getId(), 0L)))
                .toList();
    }

    public CommentResponse toSingleComment(CommentEntity comment) {
        return toCommentResponse(comment, 0);
    }

    private CommentResponse toCommentResponse(CommentEntity comment, long replyCount) {
        CommentResponse mapped = contentDtoMapper.toCommentResponse(comment);
        return new CommentResponse(
                mapped.id(),
                mapped.parentId(),
                mapped.rootId(),
                mapped.depth(),
                mapped.content(),
                mapped.author(),
                mapped.attachments(),
                mapped.createdAt(),
                mapped.updatedAt(),
                replyCount
        );
    }
}

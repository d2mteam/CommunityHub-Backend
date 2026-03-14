package com.app.communityhub.content;

import com.app.communityhub.content.dto.AuthorSummaryResponse;
import com.app.communityhub.content.dto.CommentPageResponse;
import com.app.communityhub.content.dto.CommentResponse;
import com.app.communityhub.content.dto.PostResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentMapper {

    private final ContentRecordMapper contentRecordMapper;

    public PostResponse toPostResponse(PostEntity post) {
        return contentRecordMapper.toPostResponse(post);
    }

    public CommentPageResponse toCommentPage(List<CommentEntity> comments, int offset, int limit, java.util.function.ToLongFunction<CommentEntity> replyCounter) {
        int safeOffset = Math.max(offset, 0);
        int safeLimit = Math.max(limit, 1);
        if (safeOffset >= comments.size()) {
            return new CommentPageResponse(List.of(), null, false);
        }
        int toIndex = Math.min(safeOffset + safeLimit, comments.size());
        List<CommentResponse> items = comments.subList(safeOffset, toIndex).stream()
                .map(comment -> toCommentResponse(comment, replyCounter.applyAsLong(comment)))
                .toList();
        boolean hasMore = toIndex < comments.size();
        return new CommentPageResponse(items, hasMore ? toIndex : null, hasMore);
    }

    public CommentResponse toSingleComment(CommentEntity comment) {
        return toCommentResponse(comment, 0);
    }

    private CommentResponse toCommentResponse(CommentEntity comment, long replyCount) {
        CommentResponse mapped = contentRecordMapper.toCommentResponse(comment);
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

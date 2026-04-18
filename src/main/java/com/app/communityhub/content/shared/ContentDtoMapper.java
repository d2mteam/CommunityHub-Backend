package com.app.communityhub.content.shared;

import com.app.communityhub.content.comment.CommentEntity;
import com.app.communityhub.content.comment.CommentResponse;
import com.app.communityhub.content.post.PostEntity;
import com.app.communityhub.content.post.PostResponse;
import com.app.communityhub.user.profile.ProfileMapper;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {ProfileMapper.class, AttachmentMapper.class}
)
public interface ContentDtoMapper {

    @Mapping(target = "author", source = "author")
    @Mapping(target = "id", expression = "java(String.valueOf(post.getId()))")
    @Mapping(target = "isEdited", expression = "java(post.getEditedAt() != null)")
    PostResponse toPostResponse(PostEntity post);

    @Mapping(target = "author", source = "author")
    @Mapping(target = "id", expression = "java(String.valueOf(comment.getId()))")
    @Mapping(target = "parentId", expression = "java(comment.getParent() == null ? null : String.valueOf(comment.getParent().getId()))")
    @Mapping(target = "rootId", expression = "java(comment.getRoot() == null ? null : String.valueOf(comment.getRoot().getId()))")
    @Mapping(target = "isEdited", expression = "java(comment.getEditedAt() != null)")
    @Mapping(target = "replyCount", ignore = true)
    CommentResponse toCommentResponse(CommentEntity comment);
}

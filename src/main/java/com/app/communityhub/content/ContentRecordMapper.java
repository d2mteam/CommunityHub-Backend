package com.app.communityhub.content;

import com.app.communityhub.content.dto.CommentResponse;
import com.app.communityhub.content.dto.PostResponse;
import com.app.communityhub.media.MediaMapper;
import com.app.communityhub.user.UserMapper;
import com.app.communityhub.user.dto.MediaRefResponse;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        uses = {UserMapper.class, MediaMapper.class}
)
public interface ContentRecordMapper {

    @Mapping(target = "author", source = "author")
    @Mapping(target = "attachments", expression = "java(mapPostAttachments(post.getAttachments()))")
    PostResponse toPostResponse(PostEntity post);

    @Mapping(target = "author", source = "author")
    @Mapping(target = "attachments", expression = "java(mapCommentAttachments(comment.getAttachments()))")
    @Mapping(target = "parentId", expression = "java(comment.getParent() == null ? null : comment.getParent().getId())")
    @Mapping(target = "rootId", expression = "java(comment.getRoot() == null ? null : comment.getRoot().getId())")
    @Mapping(target = "replyCount", ignore = true)
    CommentResponse toCommentResponse(CommentEntity comment);

    default List<MediaRefResponse> mapPostAttachments(List<PostAttachmentEntity> attachments) {
        return attachments.stream().map(PostAttachmentEntity::getMediaAsset).map(this::toMediaRef).toList();
    }

    default List<MediaRefResponse> mapCommentAttachments(List<CommentAttachmentEntity> attachments) {
        return attachments.stream().map(CommentAttachmentEntity::getMediaAsset).map(this::toMediaRef).toList();
    }

    MediaRefResponse toMediaRef(com.app.communityhub.media.MediaAssetEntity mediaAsset);
}

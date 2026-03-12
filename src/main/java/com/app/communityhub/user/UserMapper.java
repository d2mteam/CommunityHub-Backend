package com.app.communityhub.user;

import com.app.communityhub.content.dto.AuthorSummaryResponse;
import com.app.communityhub.media.MediaMapper;
import com.app.communityhub.user.dto.ProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = MediaMapper.class)
public interface UserMapper {

    @Mapping(target = "avatar", source = "avatarMedia")
    ProfileResponse toProfileResponse(UserEntity user);

    @Mapping(target = "avatar", source = "avatarMedia")
    AuthorSummaryResponse toAuthorSummary(UserEntity user);
}

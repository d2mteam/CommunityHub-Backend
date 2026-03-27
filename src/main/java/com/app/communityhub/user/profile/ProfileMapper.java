package com.app.communityhub.user.profile;

import com.app.communityhub.content.shared.AuthorSummaryResponse;
import com.app.communityhub.media.api.MediaMapper;
import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.profile.dto.ProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE, uses = MediaMapper.class)
public interface ProfileMapper {

    @Mapping(target = "avatar", source = "avatarMedia")
    ProfileResponse toProfileResponse(UserEntity user);

    @Mapping(target = "avatar", source = "avatarMedia")
    AuthorSummaryResponse toAuthorSummary(UserEntity user);
}

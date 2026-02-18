package com.app.communityhub.user;

import com.app.communityhub.user.dto.ProfileResponse;
import org.mapstruct.Mapper;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface UserMapper {

    ProfileResponse toProfileResponse(UserEntity user);
}

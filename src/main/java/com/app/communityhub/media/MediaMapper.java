package com.app.communityhub.media;

import com.app.communityhub.user.dto.MediaRefResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface MediaMapper {

    @Mapping(target = "mediaKey", source = "mediaKey")
    @Mapping(target = "mimeType", source = "mimeType")
    @Mapping(target = "width", source = "width")
    @Mapping(target = "height", source = "height")
    MediaRefResponse toMediaRef(MediaAssetEntity mediaAsset);
}

package com.app.communityhub.user;

import com.app.communityhub.media.MediaAssetEntity;
import com.app.communityhub.media.MediaMapper;
import com.app.communityhub.user.dto.MediaRefResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MediaRefAdapter {

    private final MediaMapper mediaMapper;

    public MediaRefResponse toMediaRef(MediaAssetEntity mediaAsset) {
        return mediaMapper.toMediaRef(mediaAsset);
    }
}

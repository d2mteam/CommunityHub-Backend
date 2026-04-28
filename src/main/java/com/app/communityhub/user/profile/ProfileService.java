package com.app.communityhub.user.profile;

import com.app.communityhub.common.AppException;
import com.app.communityhub.media.MediaAssetEntity;
import com.app.communityhub.media.attachment.MediaAttachmentService;
import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.UserRepository;
import com.app.communityhub.user.profile.dto.MediaRefResponse;
import com.app.communityhub.user.profile.dto.ProfileResponse;
import com.app.communityhub.user.profile.dto.UpdateAvatarRequest;
import com.app.communityhub.user.profile.dto.UpdateProfileRequest;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private final UserRepository userRepository;
    private final MediaAttachmentService mediaAttachmentService;
    private final ProfileMapper profileMapper;
    private final MediaRefAdapter mediaRefAdapter;

    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile(UUID userId) {
        UserEntity user = getUser(userId);
        return toProfileResponse(user);
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        UserEntity user = getUser(userId);
        String normalizedUsername = request.username().trim();
        if (!user.getUsername().equalsIgnoreCase(normalizedUsername)
                && userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            log.warn("Profile update rejected because username is already taken [userId={}, username={}]", userId, normalizedUsername);
            throw new AppException(HttpStatus.CONFLICT, "Username is already taken");
        }
        user.setUsername(normalizedUsername);
        log.info("Updated profile username [userId={}, username={}]", userId, normalizedUsername);
        return toProfileResponse(user);
    }

    @Transactional
    public ProfileResponse updateAvatar(UUID userId, UpdateAvatarRequest request) {
        UserEntity user = getUser(userId);
        if (user.getAvatarMedia() != null && user.getAvatarMedia().getMediaKey().equals(request.mediaKey())) {
            log.info("Avatar update skipped because requested media is already the current avatar [userId={}, mediaKey={}]", userId, request.mediaKey());
            return toProfileResponse(user);
        }
        MediaAssetEntity avatarMedia = mediaAttachmentService.prepareAttachment(userId, request.mediaKey());
        if (user.getAvatarMedia() != null) {
            mediaAttachmentService.markOrphaned(user.getAvatarMedia());
        }
        mediaAttachmentService.markAttached(List.of(avatarMedia));
        user.setAvatarMedia(avatarMedia);
        log.info("Updated profile avatar [userId={}, mediaKey={}]", userId, request.mediaKey());
        return toProfileResponse(user);
    }

    public ProfileResponse toProfileResponse(UserEntity user) {
        return profileMapper.toProfileResponse(user);
    }

    public MediaRefResponse toMediaRef(MediaAssetEntity mediaAsset) {
        return mediaRefAdapter.toMediaRef(mediaAsset);
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
    }
}

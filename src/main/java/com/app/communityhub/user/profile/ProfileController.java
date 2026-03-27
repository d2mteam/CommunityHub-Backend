package com.app.communityhub.user.profile;

import com.app.communityhub.auth.security.CurrentUserService;
import com.app.communityhub.user.profile.dto.ProfileResponse;
import com.app.communityhub.user.profile.dto.UpdateAvatarRequest;
import com.app.communityhub.user.profile.dto.UpdateProfileRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileController {

    private final CurrentUserService currentUserService;
    private final ProfileService profileService;

    @PatchMapping
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return profileService.updateProfile(currentUserService.requireUserId(), request);
    }

    @PostMapping("/avatar")
    public ProfileResponse updateAvatar(@Valid @RequestBody UpdateAvatarRequest request) {
        return profileService.updateAvatar(currentUserService.requireUserId(), request);
    }
}

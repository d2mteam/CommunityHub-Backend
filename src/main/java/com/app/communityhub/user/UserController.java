package com.app.communityhub.user;

import com.app.communityhub.auth.security.CurrentUserService;
import com.app.communityhub.user.dto.ProfileResponse;
import com.app.communityhub.user.dto.UpdateAvatarRequest;
import com.app.communityhub.user.dto.UpdateProfileRequest;
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
public class UserController {

    private final CurrentUserService currentUserService;
    private final UserService userService;

    @PatchMapping
    public ProfileResponse updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return userService.updateProfile(currentUserService.requireUserId(), request);
    }

    @PostMapping("/avatar")
    public ProfileResponse updateAvatar(@Valid @RequestBody UpdateAvatarRequest request) {
        return userService.updateAvatar(currentUserService.requireUserId(), request);
    }
}

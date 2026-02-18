package com.app.communityhub.user;

import com.app.communityhub.common.AppException;
import com.app.communityhub.user.dto.ProfileResponse;
import com.app.communityhub.user.dto.UpdateProfileRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Transactional(readOnly = true)
    public ProfileResponse getCurrentProfile(UUID userId) {
        return toProfileResponse(getUser(userId));
    }

    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        UserEntity user = getUser(userId);
        String normalizedUsername = request.username().trim();
        if (!user.getUsername().equalsIgnoreCase(normalizedUsername)
                && userRepository.existsByUsernameIgnoreCase(normalizedUsername)) {
            throw new AppException(HttpStatus.CONFLICT, "Username is already taken");
        }
        user.setUsername(normalizedUsername);
        return toProfileResponse(user);
    }

    public ProfileResponse toProfileResponse(UserEntity user) {
        return userMapper.toProfileResponse(user);
    }

    private UserEntity getUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.NOT_FOUND, "User not found"));
    }
}

package com.app.communityhub.auth.security;

import com.app.communityhub.common.AppException;
import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserDetailsLookupService {

    private final UserRepository userRepository;

    public AuthUser loadById(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));
        return toAuthUser(user);
    }

    public AuthUser loadByUsername(String username) {
        UserEntity user = userRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        return toAuthUser(user);
    }

    private AuthUser toAuthUser(UserEntity user) {
        return AuthUser.builder()
                .id(user.getId())
                .username(user.getUsername())
                .passwordHash(user.getPasswordHash())
                .build();
    }
}

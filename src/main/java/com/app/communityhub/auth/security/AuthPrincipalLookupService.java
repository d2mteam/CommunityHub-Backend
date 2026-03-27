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
public class AuthPrincipalLookupService {

    private final UserRepository userRepository;

    public AuthPrincipal loadById(UUID userId) {
        UserEntity user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "User not found"));
        return new AuthPrincipal(user.getId(), user.getUsername());
    }
}

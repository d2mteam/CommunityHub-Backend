package com.app.communityhub.auth.password;

import com.app.communityhub.auth.api.AuthResponse;
import com.app.communityhub.auth.api.LoginRequest;
import com.app.communityhub.auth.api.RegisterRequest;
import com.app.communityhub.auth.session.AuthSessionService;
import com.app.communityhub.common.AppException;
import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PasswordAuthService {

    private final UserRepository userRepository;
    private final PasswordCredentialRepository passwordCredentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthSessionService authSessionService;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByUsernameIgnoreCase(request.username())) {
            throw new AppException(HttpStatus.CONFLICT, "Username is already taken");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        UserEntity saved = userRepository.save(user);

        PasswordCredentialEntity passwordCredential = new PasswordCredentialEntity();
        passwordCredential.setUser(saved);
        passwordCredential.setPasswordHash(passwordEncoder.encode(request.password()));
        passwordCredentialRepository.save(passwordCredential);

        return authSessionService.issueTokensForUser(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        PasswordCredentialEntity passwordCredential = passwordCredentialRepository
                .findByUserUsernameIgnoreCase(request.username().trim())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials"));
        if (!passwordEncoder.matches(request.password(), passwordCredential.getPasswordHash())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        return authSessionService.issueTokensForUser(passwordCredential.getUser());
    }
}

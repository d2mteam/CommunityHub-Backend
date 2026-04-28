package com.app.communityhub.auth.password;

import com.app.communityhub.auth.api.AuthResponse;
import com.app.communityhub.auth.api.LoginRequest;
import com.app.communityhub.auth.api.RegisterRequest;
import com.app.communityhub.auth.session.AuthSessionService;
import com.app.communityhub.common.AppException;
import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
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
            log.warn("Registration rejected because username is already taken [username={}]", request.username().trim());
            throw new AppException(HttpStatus.CONFLICT, "Username is already taken");
        }

        UserEntity user = new UserEntity();
        user.setUsername(request.username().trim());
        UserEntity saved = userRepository.save(user);

        PasswordCredentialEntity passwordCredential = PasswordCredentialEntity.create(
                saved,
                passwordEncoder.encode(request.password())
        );
        passwordCredentialRepository.save(passwordCredential);
        log.info("Registered new password user [userId={}, username={}]", saved.getId(), saved.getUsername());

        return authSessionService.issueTokensForUser(saved);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String normalizedUsername = request.username().trim();
        PasswordCredentialEntity passwordCredential = passwordCredentialRepository
                .findByUserUsernameIgnoreCase(normalizedUsername)
                .orElseThrow(() -> {
                    log.warn("Password login rejected because username was not found [username={}]", normalizedUsername);
                    return new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
                });
        if (!passwordEncoder.matches(request.password(), passwordCredential.getPasswordHash())) {
            log.warn(
                    "Password login rejected because password did not match [userId={}, username={}]",
                    passwordCredential.getUser().getId(),
                    passwordCredential.getUser().getUsername()
            );
            throw new AppException(HttpStatus.UNAUTHORIZED, "Invalid credentials");
        }
        log.info(
                "Password login succeeded [userId={}, username={}]",
                passwordCredential.getUser().getId(),
                passwordCredential.getUser().getUsername()
        );
        return authSessionService.issueTokensForUser(passwordCredential.getUser());
    }
}

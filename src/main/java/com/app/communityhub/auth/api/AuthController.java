package com.app.communityhub.auth.api;

import com.app.communityhub.auth.oauth.OAuthLoginFlowService;
import com.app.communityhub.auth.oauth.OAuthLoginTicketService;
import com.app.communityhub.auth.password.PasswordAuthService;
import com.app.communityhub.auth.security.CurrentUserService;
import com.app.communityhub.auth.session.AuthSessionService;
import com.app.communityhub.common.AppException;
import com.app.communityhub.user.profile.ProfileService;
import com.app.communityhub.user.profile.dto.ProfileResponse;
import jakarta.validation.Valid;
import java.net.URI;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final PasswordAuthService passwordAuthService;
    private final AuthSessionService authSessionService;
    private final OAuthLoginFlowService oauthLoginFlowService;
    private final OAuthLoginTicketService oauthLoginTicketService;
    private final CurrentUserService currentUserService;
    private final ProfileService profileService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(passwordAuthService.register(request));
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return passwordAuthService.login(request);
    }

    @GetMapping("/oauth/{provider}/start")
    public ResponseEntity<Void> startOAuth(
            @PathVariable String provider,
            @RequestParam(required = false) String returnTo
    ) {
        return redirect(oauthLoginFlowService.start(provider, returnTo));
    }

    @GetMapping("/oauth/{provider}/callback")
    public ResponseEntity<Void> completeOAuth(
            @PathVariable String provider,
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error
    ) {
        if (error != null && !error.isBlank()) {
            return redirect(oauthLoginFlowService.frontendErrorRedirect(error, "/"));
        }
        try {
            return redirect(oauthLoginFlowService.complete(provider, code, state));
        } catch (AppException exception) {
            return redirect(oauthLoginFlowService.frontendErrorRedirect("oauth_failed", "/"));
        }
    }

    @PostMapping("/oauth/exchange")
    public AuthResponse exchangeOAuthTicket(@Valid @RequestBody OAuthExchangeRequest request) {
        return oauthLoginTicketService.exchange(request.ticket());
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest request) {
        return authSessionService.refresh(request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authSessionService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ProfileResponse me() {
        return profileService.getCurrentProfile(currentUserService.requireUserId());
    }

    private ResponseEntity<Void> redirect(URI location) {
        return ResponseEntity.status(HttpStatus.FOUND).location(location).build();
    }
}

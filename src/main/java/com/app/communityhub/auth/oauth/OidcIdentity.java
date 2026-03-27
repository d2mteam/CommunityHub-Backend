package com.app.communityhub.auth.oauth;

public record OidcIdentity(
        String subject,
        String email,
        Boolean emailVerified,
        String name,
        String preferredUsername
) {
}

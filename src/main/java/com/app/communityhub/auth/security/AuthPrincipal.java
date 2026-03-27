package com.app.communityhub.auth.security;

import java.util.UUID;

public record AuthPrincipal(
        UUID id,
        String username
) {
}

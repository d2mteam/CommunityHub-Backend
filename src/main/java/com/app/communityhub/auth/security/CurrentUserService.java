package com.app.communityhub.auth.security;

import com.app.communityhub.common.AppException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserService {

    public AuthUser requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthUser authUser)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }
        return authUser;
    }

    public UUID requireUserId() {
        return requireUser().id();
    }
}

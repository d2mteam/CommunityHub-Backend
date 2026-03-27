package com.app.communityhub.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

record OidcUserInfo(
        String sub,
        String email,
        @JsonProperty("email_verified") Boolean emailVerified,
        String name,
        @JsonProperty("preferred_username") String preferredUsername
) {
}

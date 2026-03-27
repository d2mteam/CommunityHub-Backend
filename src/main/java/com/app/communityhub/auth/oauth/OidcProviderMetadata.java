package com.app.communityhub.auth.oauth;

import com.fasterxml.jackson.annotation.JsonProperty;

record OidcProviderMetadata(
        String issuer,
        @JsonProperty("authorization_endpoint") String authorizationEndpoint,
        @JsonProperty("token_endpoint") String tokenEndpoint,
        @JsonProperty("userinfo_endpoint") String userInfoEndpoint,
        @JsonProperty("jwks_uri") String jwksUri
) {
}

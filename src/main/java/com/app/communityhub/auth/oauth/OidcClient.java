package com.app.communityhub.auth.oauth;

import com.app.communityhub.common.AppException;
import com.app.communityhub.config.AppProperties;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Component
@RequiredArgsConstructor
public class OidcClient {

    private final RestClient restClient = RestClient.create();
    private final Map<String, OidcProviderMetadata> metadataCache = new ConcurrentHashMap<>();
    private final Map<String, JwtDecoder> decoderCache = new ConcurrentHashMap<>();

    public OidcProviderMetadata discover(String providerName, AppProperties.OAuth.Provider provider) {
        return metadataCache.computeIfAbsent(providerName, ignored -> {
            String issuer = trimTrailingSlash(provider.getIssuerUri());
            return restClient.get()
                    .uri(issuer + "/.well-known/openid-configuration")
                    .retrieve()
                    .body(OidcProviderMetadata.class);
        });
    }

    public OidcTokenResponse exchangeAuthorizationCode(
            String providerName,
            AppProperties.OAuth.Provider provider,
            String code,
            String codeVerifier
    ) {
        OidcProviderMetadata metadata = discover(providerName, provider);
        MultiValueMap<String, String> body = new LinkedMultiValueMap<>();
        body.add("grant_type", "authorization_code");
        body.add("code", code);
        body.add("redirect_uri", provider.getRedirectUri());
        body.add("client_id", provider.getClientId());
        body.add("code_verifier", codeVerifier);

        try {
            return restClient.post()
                    .uri(metadata.tokenEndpoint())
                    .headers(headers -> headers.setBasicAuth(provider.getClientId(), provider.getClientSecret()))
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(body)
                    .retrieve()
                    .body(OidcTokenResponse.class);
        } catch (RuntimeException exception) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "OAuth token exchange failed");
        }
    }

    public OidcIdentity loadIdentity(
            String providerName,
            AppProperties.OAuth.Provider provider,
            OidcTokenResponse tokenResponse,
            String expectedNonce
    ) {
        if (tokenResponse == null || tokenResponse.idToken() == null || tokenResponse.accessToken() == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "OAuth token response is incomplete");
        }

        Jwt jwt = decodeIdToken(providerName, provider, tokenResponse.idToken());
        validateAudience(jwt, provider.getClientId());
        validateNonce(jwt, expectedNonce);

        OidcUserInfo userInfo = fetchUserInfo(providerName, provider, tokenResponse.accessToken());
        String subject = firstNonBlank(userInfo == null ? null : userInfo.sub(), jwt.getSubject());
        if (subject == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "OAuth subject is missing");
        }

        return new OidcIdentity(
                subject,
                firstNonBlank(userInfo == null ? null : userInfo.email(), jwt.getClaimAsString("email")),
                firstNonNull(userInfo == null ? null : userInfo.emailVerified(), jwt.getClaim("email_verified")),
                firstNonBlank(userInfo == null ? null : userInfo.name(), jwt.getClaimAsString("name")),
                firstNonBlank(
                        userInfo == null ? null : userInfo.preferredUsername(),
                        jwt.getClaimAsString("preferred_username")
                )
        );
    }

    public String authorizationEndpoint(String providerName, AppProperties.OAuth.Provider provider) {
        return discover(providerName, provider).authorizationEndpoint();
    }

    private Jwt decodeIdToken(String providerName, AppProperties.OAuth.Provider provider, String idToken) {
        JwtDecoder decoder = decoderCache.computeIfAbsent(providerName, ignored -> {
            OidcProviderMetadata metadata = discover(providerName, provider);
            NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(metadata.jwksUri()).build();
            OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                    JwtValidators.createDefaultWithIssuer(trimTrailingSlash(provider.getIssuerUri()))
            );
            jwtDecoder.setJwtValidator(validator);
            return jwtDecoder;
        });

        try {
            return decoder.decode(idToken);
        } catch (JwtException exception) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "OAuth ID token validation failed");
        }
    }

    private OidcUserInfo fetchUserInfo(
            String providerName,
            AppProperties.OAuth.Provider provider,
            String accessToken
    ) {
        OidcProviderMetadata metadata = discover(providerName, provider);
        if (metadata.userInfoEndpoint() == null || metadata.userInfoEndpoint().isBlank()) {
            return null;
        }

        try {
            return restClient.get()
                    .uri(metadata.userInfoEndpoint())
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve()
                    .body(OidcUserInfo.class);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private void validateAudience(Jwt jwt, String clientId) {
        if (!jwt.getAudience().contains(clientId)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "OAuth ID token audience mismatch");
        }
    }

    private void validateNonce(Jwt jwt, String expectedNonce) {
        String actualNonce = jwt.getClaimAsString("nonce");
        if (actualNonce == null || !actualNonce.equals(expectedNonce)) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "OAuth nonce mismatch");
        }
    }

    private String trimTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }
        return value;
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    private Boolean firstNonNull(Boolean first, Object second) {
        if (first != null) {
            return first;
        }
        if (second instanceof Boolean booleanValue) {
            return booleanValue;
        }
        return null;
    }
}

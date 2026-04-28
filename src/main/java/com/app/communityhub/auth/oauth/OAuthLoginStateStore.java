package com.app.communityhub.auth.oauth;

import com.app.communityhub.common.AppException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class OAuthLoginStateStore {

    private static final String KEY_PREFIX = "oauth:state:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OAuthTokenSupport tokenSupport;

    public void save(
            String rawState,
            String provider,
            String codeVerifier,
            String nonce,
            String returnTo,
            String redirectUri,
            Duration ttl
    ) {
        StoredState storedState = new StoredState(
                provider,
                codeVerifier,
                nonce,
                returnTo,
                redirectUri,
                Instant.now().plus(ttl)
        );
        redisTemplate.opsForValue().set(key(rawState), serialize(storedState), ttl);
        log.debug("Stored OAuth login state [provider={}, returnTo={}, redirectUri={}]", provider, returnTo, redirectUri);
    }

    public StoredState consume(String provider, String rawState) {
        String payload = redisTemplate.opsForValue().getAndDelete(key(rawState));
        if (payload == null) {
            log.warn("OAuth state was not found during callback [provider={}]", provider);
            throw new AppException(HttpStatus.UNAUTHORIZED, "OAuth state not found");
        }

        StoredState storedState = deserialize(payload);
        if (!storedState.provider().equals(provider) || storedState.expiresAt().isBefore(Instant.now())) {
            log.warn(
                    "OAuth state validation failed [expectedProvider={}, actualProvider={}, expired={}]",
                    provider,
                    storedState.provider(),
                    storedState.expiresAt().isBefore(Instant.now())
            );
            throw new AppException(HttpStatus.UNAUTHORIZED, "OAuth state has expired");
        }
        log.debug("Consumed OAuth login state [provider={}, returnTo={}]", provider, storedState.returnTo());
        return storedState;
    }

    public boolean exists(String rawState) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(key(rawState)));
    }

    private String key(String rawState) {
        return KEY_PREFIX + tokenSupport.hash(rawState);
    }

    private String serialize(StoredState storedState) {
        try {
            return objectMapper.writeValueAsString(storedState);
        } catch (JsonProcessingException exception) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store OAuth state");
        }
    }

    private StoredState deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, StoredState.class);
        } catch (IOException exception) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read OAuth state");
        }
    }

    public record StoredState(
            String provider,
            String codeVerifier,
            String nonce,
            String returnTo,
            String redirectUri,
            Instant expiresAt
    ) {
    }
}

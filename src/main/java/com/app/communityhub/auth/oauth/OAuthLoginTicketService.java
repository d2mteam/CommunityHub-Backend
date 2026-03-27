package com.app.communityhub.auth.oauth;

import com.app.communityhub.auth.api.AuthResponse;
import com.app.communityhub.auth.session.AuthSessionService;
import com.app.communityhub.common.AppException;
import com.app.communityhub.config.AppProperties;
import com.app.communityhub.user.UserEntity;
import com.app.communityhub.user.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OAuthLoginTicketService {

    private static final String KEY_PREFIX = "oauth:ticket:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final OAuthTokenSupport tokenSupport;
    private final UserRepository userRepository;
    private final AuthSessionService authSessionService;
    private final AppProperties appProperties;

    public String createTicket(UserEntity user, String returnTo) {
        String rawTicket = tokenSupport.randomToken();
        StoredTicket ticket = new StoredTicket(
                user.getId(),
                returnTo,
                Instant.now().plus(appProperties.getOauth().getTicketTtl())
        );
        redisTemplate.opsForValue().set(
                key(rawTicket),
                serialize(ticket),
                appProperties.getOauth().getTicketTtl()
        );
        return rawTicket;
    }

    public AuthResponse exchange(String ticket) {
        String payload = redisTemplate.opsForValue().getAndDelete(key(ticket));
        if (payload == null) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "OAuth login ticket not found");
        }

        StoredTicket storedTicket = deserialize(payload);
        if (storedTicket.expiresAt().isBefore(Instant.now())) {
            throw new AppException(HttpStatus.UNAUTHORIZED, "OAuth login ticket has expired");
        }

        UserEntity user = userRepository.findById(storedTicket.userId())
                .orElseThrow(() -> new AppException(HttpStatus.UNAUTHORIZED, "OAuth login ticket not found"));
        return authSessionService.issueTokensForUser(user);
    }

    private String key(String rawTicket) {
        return KEY_PREFIX + tokenSupport.hash(rawTicket);
    }

    private String serialize(StoredTicket storedTicket) {
        try {
            return objectMapper.writeValueAsString(storedTicket);
        } catch (JsonProcessingException exception) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to store OAuth login ticket");
        }
    }

    private StoredTicket deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, StoredTicket.class);
        } catch (IOException exception) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to read OAuth login ticket");
        }
    }

    private record StoredTicket(
            java.util.UUID userId,
            String returnTo,
            Instant expiresAt
    ) {
    }
}

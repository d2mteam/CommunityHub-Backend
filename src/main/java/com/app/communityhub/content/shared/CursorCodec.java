package com.app.communityhub.content.shared;

import com.app.communityhub.common.AppException;
import com.app.communityhub.content.shared.SortOrder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CursorCodec {

    private final ObjectMapper objectMapper;

    public String encode(CursorToken token) {
        try {
            byte[] payload = objectMapper.writeValueAsBytes(token);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
        } catch (JsonProcessingException exception) {
            throw new AppException(HttpStatus.INTERNAL_SERVER_ERROR, "Unable to encode pagination cursor");
        }
    }

    public CursorToken decode(String rawCursor, SortOrder expectedSort, Long expectedParentId) {
        if (rawCursor == null || rawCursor.isBlank()) {
            return null;
        }

        try {
            byte[] payload = Base64.getUrlDecoder().decode(rawCursor.getBytes(StandardCharsets.UTF_8));
            CursorToken token = objectMapper.readValue(payload, CursorToken.class);
            if (token.id() == null || token.sort() == null) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Invalid cursor");
            }
            if (token.sort() != expectedSort) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Cursor does not match the selected sort order");
            }
            if (!java.util.Objects.equals(token.parentId(), expectedParentId)) {
                throw new AppException(HttpStatus.BAD_REQUEST, "Cursor does not match the selected comment level");
            }
            return token;
        } catch (IllegalArgumentException | IOException exception) {
            throw new AppException(HttpStatus.BAD_REQUEST, "Invalid cursor");
        }
    }
}

package com.app.communityhub.content.shared;

import com.app.communityhub.common.AppException;
import org.springframework.http.HttpStatus;

public final class ContentIdParser {

    private ContentIdParser() {
    }

    public static Long requireId(String rawValue, String label) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new AppException(HttpStatus.BAD_REQUEST, label + " is required");
        }
        try {
            long parsed = Long.parseLong(rawValue);
            if (parsed <= 0) {
                throw new NumberFormatException("ID must be positive");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new AppException(HttpStatus.BAD_REQUEST, label + " must be a numeric id");
        }
    }

    public static Long optionalId(String rawValue, String label) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }
        return requireId(rawValue, label);
    }
}

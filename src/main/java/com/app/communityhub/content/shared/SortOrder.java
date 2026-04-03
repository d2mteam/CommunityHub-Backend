package com.app.communityhub.content.shared;

import com.app.communityhub.common.AppException;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;
import org.springframework.http.HttpStatus;

public enum SortOrder {
    NEWEST("newest"),
    OLDEST("oldest");

    private final String value;

    SortOrder(String value) {
        this.value = value;
    }

    @JsonValue
    public String value() {
        return value;
    }

    public static SortOrder from(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return NEWEST;
        }
        return switch (rawValue.trim().toLowerCase(Locale.ROOT)) {
            case "newest" -> NEWEST;
            case "oldest" -> OLDEST;
            default -> throw new AppException(HttpStatus.BAD_REQUEST, "Unsupported sort order: " + rawValue);
        };
    }
}

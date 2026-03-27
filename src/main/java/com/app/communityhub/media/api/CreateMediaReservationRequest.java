package com.app.communityhub.media.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateMediaReservationRequest(
        @NotBlank String fileName,
        @NotBlank String mimeType,
        @Positive long sizeBytes
) {
}

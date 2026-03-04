package com.app.communityhub.media.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

public record CreateMediaReservationRequest(
        @NotBlank String fileName,
        @NotBlank String mimeType,
        @Positive long sizeBytes
) {
}

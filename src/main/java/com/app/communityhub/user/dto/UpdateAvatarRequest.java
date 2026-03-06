package com.app.communityhub.user.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAvatarRequest(@NotBlank String mediaKey) {
}

package com.app.communityhub.user.profile.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdateAvatarRequest(@NotBlank String mediaKey) {
}

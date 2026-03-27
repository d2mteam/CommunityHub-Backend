package com.app.communityhub.auth.api;

import jakarta.validation.constraints.NotBlank;

public record OAuthExchangeRequest(@NotBlank String ticket) {
}

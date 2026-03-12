package com.app.communityhub.content.dto;

import com.app.communityhub.user.dto.MediaRefResponse;
import java.util.UUID;

public record AuthorSummaryResponse(
        UUID id,
        String username,
        MediaRefResponse avatar
) {
}

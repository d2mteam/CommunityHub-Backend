package com.app.communityhub.content.shared;

import com.app.communityhub.user.profile.dto.MediaRefResponse;
import java.util.UUID;

public record AuthorSummaryResponse(
        UUID id,
        String username,
        MediaRefResponse avatar
) {
}

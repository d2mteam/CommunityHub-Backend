package com.app.communityhub.content.shared;

import com.app.communityhub.content.shared.SortOrder;

public record CursorToken(
        Long id,
        SortOrder sort,
        Long parentId
) {
}

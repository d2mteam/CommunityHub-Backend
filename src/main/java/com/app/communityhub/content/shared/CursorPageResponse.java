package com.app.communityhub.content.shared;

import java.util.List;

public record CursorPageResponse<T>(
        List<T> items,
        String nextCursor,
        boolean hasMore,
        SortOrder sort
) {
}

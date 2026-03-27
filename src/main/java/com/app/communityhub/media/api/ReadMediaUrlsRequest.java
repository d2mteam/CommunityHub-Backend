package com.app.communityhub.media.api;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record ReadMediaUrlsRequest(@NotEmpty List<String> mediaKeys) {
}

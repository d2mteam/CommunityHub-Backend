package com.app.communityhub.content;

import com.app.communityhub.content.dto.PostResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ContentMapper {

    private final ContentRecordMapper contentRecordMapper;

    public PostResponse toPostResponse(PostEntity post) {
        return contentRecordMapper.toPostResponse(post);
    }
}

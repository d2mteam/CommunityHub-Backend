package com.app.communityhub.content;

import com.app.communityhub.auth.security.CurrentUserService;
import com.app.communityhub.content.dto.CommentPageResponse;
import com.app.communityhub.content.dto.PostResponse;
import com.app.communityhub.content.dto.CreatePostRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/posts")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;
    private final CommentService commentService;
    private final CurrentUserService currentUserService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PostResponse create(@Valid @RequestBody CreatePostRequest request) {
        return postService.create(currentUserService.requireUserId(), request);
    }

    @GetMapping
    public List<PostResponse> list() {
        return postService.list();
    }

    @GetMapping("/{postId}")
    public PostResponse get(@PathVariable UUID postId) {
        return postService.get(postId);
    }

    @GetMapping("/{postId}/comments")
    public CommentPageResponse comments(
            @PathVariable UUID postId,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return commentService.getPage(postId, parentId, offset, limit);
    }
}

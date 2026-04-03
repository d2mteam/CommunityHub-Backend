package com.app.communityhub.content.post;

import com.app.communityhub.auth.security.CurrentUserService;
import com.app.communityhub.content.comment.CommentResponse;
import com.app.communityhub.content.comment.CommentService;
import com.app.communityhub.content.comment.CommentResponse;
import com.app.communityhub.content.shared.CursorPageResponse;
import com.app.communityhub.content.shared.SortOrder;
import jakarta.validation.Valid;
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
    public CursorPageResponse<PostResponse> list(
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return postService.list(cursor, SortOrder.from(sort), limit);
    }

    @GetMapping("/{postId}")
    public PostResponse get(@PathVariable Long postId) {
        return postService.get(postId);
    }

    @GetMapping("/{postId}/comments")
    public CursorPageResponse<CommentResponse> comments(
            @PathVariable Long postId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "newest") String sort,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {
        return commentService.getPage(postId, parentId, SortOrder.from(sort), cursor, limit);
    }
}

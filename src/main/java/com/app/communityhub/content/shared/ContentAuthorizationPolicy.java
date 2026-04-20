package com.app.communityhub.content.shared;

import com.app.communityhub.auth.security.AuthPrincipal;
import com.app.communityhub.common.AppException;
import com.app.communityhub.content.comment.CommentEntity;
import com.app.communityhub.content.post.PostEntity;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
public class ContentAuthorizationPolicy {

    public void requireCanEditPost(AuthPrincipal actor, PostEntity post) {
        requireOwnership(actor, post.getAuthor().getId(), "You do not have permission to edit this post");
    }

    public void requireCanDeletePost(AuthPrincipal actor, PostEntity post) {
        requireOwnership(actor, post.getAuthor().getId(), "You do not have permission to delete this post");
    }

    public void requireCanEditComment(AuthPrincipal actor, CommentEntity comment) {
        requireOwnership(actor, comment.getAuthor().getId(), "You do not have permission to edit this comment");
    }

    public void requireCanDeleteComment(AuthPrincipal actor, CommentEntity comment) {
        requireOwnership(actor, comment.getAuthor().getId(), "You do not have permission to delete this comment");
    }

    private void requireOwnership(AuthPrincipal actor, UUID ownerId, String message) {
        if (actor == null || ownerId == null || !ownerId.equals(actor.id())) {
            throw new AppException(HttpStatus.FORBIDDEN, message);
        }
    }
}

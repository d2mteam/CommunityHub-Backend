package com.app.communityhub.content;

import com.app.communityhub.auth.security.AuthPrincipal;
import com.app.communityhub.common.AppException;
import com.app.communityhub.content.comment.CommentEntity;
import com.app.communityhub.content.post.PostEntity;
import com.app.communityhub.content.shared.ContentAuthorizationPolicy;
import com.app.communityhub.user.UserEntity;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ContentAuthorizationPolicyTest {

    private final ContentAuthorizationPolicy policy = new ContentAuthorizationPolicy();

    @Test
    void authorCanEditOwnedPost() {
        UUID ownerId = UUID.randomUUID();
        PostEntity post = PostEntity.builder()
                .id(100L)
                .author(user(ownerId, "author"))
                .content("Owned post")
                .build();

        assertThatCode(() -> policy.requireCanEditPost(new AuthPrincipal(ownerId, "author"), post))
                .doesNotThrowAnyException();
    }

    @Test
    void nonAuthorCannotDeleteOwnedComment() {
        UUID ownerId = UUID.randomUUID();
        CommentEntity comment = CommentEntity.builder()
                .id(200L)
                .author(user(ownerId, "author"))
                .content("Owned comment")
                .build();

        assertThatThrownBy(() -> policy.requireCanDeleteComment(new AuthPrincipal(UUID.randomUUID(), "other"), comment))
                .isInstanceOf(AppException.class)
                .hasMessage("You do not have permission to delete this comment");
    }

    private UserEntity user(UUID id, String username) {
        UserEntity user = new UserEntity();
        user.setId(id);
        user.setUsername(username);
        return user;
    }
}

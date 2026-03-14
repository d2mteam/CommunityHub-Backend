package com.app.communityhub.content;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommentRepository extends JpaRepository<CommentEntity, UUID> {

    List<CommentEntity> findAllByPostIdAndParentIsNullOrderByCreatedAtDesc(UUID postId);

    List<CommentEntity> findAllByPostIdAndParentIdOrderByCreatedAtAsc(UUID postId, UUID parentId);

    long countByParentId(UUID parentId);
}

package com.app.communityhub.content;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PostRepository extends JpaRepository<PostEntity, UUID> {

    List<PostEntity> findAllByOrderByCreatedAtDesc();
}

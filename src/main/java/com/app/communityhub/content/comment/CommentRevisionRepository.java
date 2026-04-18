package com.app.communityhub.content.comment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRevisionRepository extends JpaRepository<CommentRevisionEntity, UUID> {

    @Query("""
            select coalesce(max(r.revisionNumber), 0)
            from CommentRevisionEntity r
            where r.entityId = :entityId
            """)
    int findMaxRevisionNumberByEntityId(@Param("entityId") Long entityId);
}

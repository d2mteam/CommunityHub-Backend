package com.app.communityhub.content.post;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PostRevisionRepository extends JpaRepository<PostRevisionEntity, UUID> {

    @Query("""
            select coalesce(max(r.revisionNumber), 0)
            from PostRevisionEntity r
            where r.entityId = :entityId
            """)
    int findMaxRevisionNumberByEntityId(@Param("entityId") Long entityId);
}

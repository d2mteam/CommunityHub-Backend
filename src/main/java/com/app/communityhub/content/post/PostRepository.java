package com.app.communityhub.content.post;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Pageable;

public interface PostRepository extends JpaRepository<PostEntity, Long> {

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    @Query("""
            select p
            from PostEntity p
            order by p.id desc
            """)
    List<PostEntity> findPageNewest(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    @Query("""
            select p
            from PostEntity p
            where p.id < :id
            order by p.id desc
            """)
    List<PostEntity> findPageNewestAfter(@Param("id") Long id, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    @Query("""
            select p
            from PostEntity p
            order by p.id asc
            """)
    List<PostEntity> findPageOldest(Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    @Query("""
            select p
            from PostEntity p
            where p.id > :id
            order by p.id asc
            """)
    List<PostEntity> findPageOldestAfter(@Param("id") Long id, Pageable pageable);

    @Override
    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    java.util.Optional<PostEntity> findById(Long id);
}

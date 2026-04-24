package com.app.communityhub.content.comment;

import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommentRepository extends JpaRepository<CommentEntity, Long> {

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    @Query("""
            select c
            from CommentEntity c
            where c.post.id = :postId and c.parent is null and c.deletedAt is null
            order by c.id desc
            """)
    List<CommentEntity> findRootPageNewest(@Param("postId") Long postId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    @Query("""
            select c
            from CommentEntity c
            where c.post.id = :postId
                and c.parent is null
                and c.deletedAt is null
                and c.id < :id
            order by c.id desc
            """)
    List<CommentEntity> findRootPageNewestAfter(
            @Param("postId") Long postId,
            @Param("id") Long id,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    @Query("""
            select c
            from CommentEntity c
            where c.post.id = :postId and c.parent is null and c.deletedAt is null
            order by c.id asc
            """)
    List<CommentEntity> findRootPageOldest(@Param("postId") Long postId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    @Query("""
            select c
            from CommentEntity c
            where c.post.id = :postId
                and c.parent is null
                and c.deletedAt is null
                and c.id > :id
            order by c.id asc
            """)
    List<CommentEntity> findRootPageOldestAfter(
            @Param("postId") Long postId,
            @Param("id") Long id,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    @Query("""
            select c
            from CommentEntity c
            where c.post.id = :postId and c.parent.id = :parentId and c.deletedAt is null
            order by c.id asc
            """)
    List<CommentEntity> findReplyPage(@Param("postId") Long postId, @Param("parentId") Long parentId, Pageable pageable);

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    @Query("""
            select c
            from CommentEntity c
            where c.post.id = :postId
                and c.parent.id = :parentId
                and c.deletedAt is null
                and c.id > :id
            order by c.id asc
            """)
    List<CommentEntity> findReplyPageAfter(
            @Param("postId") Long postId,
            @Param("parentId") Long parentId,
            @Param("id") Long id,
            Pageable pageable
    );

    boolean existsByIdAndPostIdAndDeletedAtIsNull(Long id, Long postId);

    @EntityGraph(attributePaths = {"author", "author.avatarMedia"})
    java.util.Optional<CommentEntity> findByIdAndDeletedAtIsNull(Long id);

    List<CommentEntity> findAllByPostIdAndDeletedAtIsNullOrderByIdAsc(Long postId);

    @Query(value = """
            with recursive subtree as (
                select c.id
                from comments c
                where c.id = :commentId and c.deleted_at is null
                union all
                select child.id
                from comments child
                join subtree parent_tree on child.parent_id = parent_tree.id
                where child.deleted_at is null
            )
            select id from subtree order by id
            """, nativeQuery = true)
    List<Long> findVisibleSubtreeIds(@Param("commentId") Long commentId);

    @Query("""
            select c.parent.id as parentId, count(c.id) as replyCount
            from CommentEntity c
            where c.parent.id in :parentIds and c.deletedAt is null
            group by c.parent.id
            """)
    List<CommentReplyCountView> countRepliesByParentIds(@Param("parentIds") Collection<Long> parentIds);
}

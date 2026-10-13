package com.luminous.api.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.apache.ibatis.annotations.Select;
import java.util.List;

public interface CommentMapper extends BaseMapper<Comment> {
    @Select("""
        <script>
        SELECT f.id, f.votes, f.created_at, f.root_present FROM (
            SELECT id, votes, created_at, 1 AS root_present
            FROM comments WHERE post_id=#{postId} AND parent_id IS NULL
            UNION ALL
            SELECT r.parent_id AS id, 0 AS votes, MIN(r.created_at) AS created_at, 0 AS root_present
            FROM comments r LEFT JOIN comments c ON c.id=r.parent_id AND c.post_id=r.post_id
            WHERE r.post_id=#{postId} AND r.parent_id IS NOT NULL AND c.id IS NULL
            GROUP BY r.parent_id
        ) f
        <if test="cursor != null">
            WHERE f.votes &lt; #{cursor.votes}
            OR (f.votes=#{cursor.votes} AND f.created_at &gt; #{cursor.createdAt})
            OR (f.votes=#{cursor.votes} AND f.created_at=#{cursor.createdAt} AND f.id &gt; #{cursor.id})
        </if>
        ORDER BY f.votes DESC, f.created_at ASC, f.id ASC LIMIT #{limit}
        </script>
        """)
    List<CommentFloorRow> selectFloors(@Param("postId") Long postId, @Param("cursor") CommentCursor cursor, @Param("limit") int limit);

    @Select("""
        <script>
        SELECT parent_id, COUNT(*) AS reply_count FROM comments
        WHERE post_id=#{postId} AND parent_id IN
        <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
        GROUP BY parent_id
        </script>
        """)
    List<CommentReplyCount> countReplies(@Param("postId") Long postId, @Param("ids") List<Long> ids);

    @Select("""
        <script>
        SELECT * FROM comments WHERE post_id=#{postId} AND parent_id=#{rootId}
        <if test="cursor != null">
            AND (created_at &gt; #{cursor.createdAt}
            OR (created_at=#{cursor.createdAt} AND id &gt; #{cursor.id}))
        </if>
        ORDER BY created_at ASC, id ASC LIMIT #{limit}
        </script>
        """)
    List<Comment> selectReplies(@Param("postId") Long postId, @Param("rootId") Long rootId,
                                @Param("cursor") CommentCursor cursor, @Param("limit") int limit);

    @org.apache.ibatis.annotations.Select("SELECT * FROM comments WHERE id=#{id} FOR UPDATE")
    Comment selectForUpdate(@org.apache.ibatis.annotations.Param("id") Long id);

    @Update("UPDATE comments SET votes = votes + #{delta}, updated_at = NOW() WHERE id = #{commentId}")
    int updateVotesAtomic(@Param("commentId") Long commentId, @Param("delta") int delta);
}

package com.luminous.api.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PostMapper extends BaseMapper<Post> {

    @org.apache.ibatis.annotations.Select("SELECT p.* FROM posts p WHERE p.is_public=TRUE AND p.created_at>=#{since} "
            + "AND p.created_at<=#{before} AND EXISTS (SELECT 1 FROM user_follows f WHERE f.follower_id=#{userId} "
            + "AND f.following_id=p.author_id) ORDER BY p.created_at DESC,p.id DESC LIMIT #{limit} OFFSET #{offset}")
    java.util.List<Post> selectFollowedFeed(@Param("userId") Long userId,
            @Param("since") java.time.LocalDateTime since, @Param("before") java.time.LocalDateTime before,
            @Param("offset") int offset, @Param("limit") int limit);


    @Update("UPDATE posts SET comments_count = GREATEST(0, comments_count + #{delta}), updated_at = NOW() WHERE id = #{postId}")
    int updateCommentsCountAtomic(@Param("postId") Long postId, @Param("delta") int delta);

    @Update("UPDATE posts SET votes = votes + #{delta}, updated_at = NOW() WHERE id = #{postId}")
    int updateVotesAtomic(@Param("postId") Long postId, @Param("delta") int delta);
}

package com.luminous.api.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface PostMapper extends BaseMapper<Post> {

    @Update("UPDATE posts SET comments_count = GREATEST(0, comments_count + #{delta}), updated_at = NOW() WHERE id = #{postId}")
    int updateCommentsCountAtomic(@Param("postId") Long postId, @Param("delta") int delta);

    @Update("UPDATE posts SET votes = votes + #{delta}, updated_at = NOW() WHERE id = #{postId}")
    int updateVotesAtomic(@Param("postId") Long postId, @Param("delta") int delta);
}

package com.luminous.api.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CommentMapper extends BaseMapper<Comment> {

    @Update("UPDATE comments SET votes = votes + #{delta}, updated_at = NOW() WHERE id = #{commentId}")
    int updateVotesAtomic(@Param("commentId") Long commentId, @Param("delta") int delta);
}

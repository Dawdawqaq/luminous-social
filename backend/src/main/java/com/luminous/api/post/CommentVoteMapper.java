package com.luminous.api.post;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;

public interface CommentVoteMapper extends BaseMapper<CommentVote> {
    /** 先利用唯一键建立零状态，保留已有投票，避免缺行查询持有跨评论的间隙锁。 */
    @org.apache.ibatis.annotations.Insert("INSERT INTO comment_votes(id,comment_id,user_id,value) VALUES(#{id},#{commentId},#{userId},0) "
            + "ON DUPLICATE KEY UPDATE comment_id=comment_id")
    int initialize(@org.apache.ibatis.annotations.Param("id") Long id,
                   @org.apache.ibatis.annotations.Param("commentId") Long commentId,
                   @org.apache.ibatis.annotations.Param("userId") Long userId);

    @org.apache.ibatis.annotations.Select("SELECT * FROM comment_votes WHERE comment_id=#{commentId} AND user_id=#{userId} FOR UPDATE")
    CommentVote selectForUpdate(@org.apache.ibatis.annotations.Param("commentId") Long commentId,
                               @org.apache.ibatis.annotations.Param("userId") Long userId);
}

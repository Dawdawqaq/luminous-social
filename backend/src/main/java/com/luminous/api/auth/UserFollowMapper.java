package com.luminous.api.auth;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

@Mapper
public interface UserFollowMapper extends BaseMapper<UserFollow> {
    @Select("""
            SELECT u.id, u.username, u.display_name, u.avatar_url, u.bio
            FROM user_follows f JOIN users u ON u.id = f.follower_id
            WHERE f.following_id = #{userId}
            ORDER BY f.created_at DESC, f.follower_id DESC
            """)
    IPage<User> selectFollowers(Page<User> page, @Param("userId") Long userId);

    @Select("""
            SELECT u.id, u.username, u.display_name, u.avatar_url, u.bio
            FROM user_follows f JOIN users u ON u.id = f.following_id
            WHERE f.follower_id = #{userId}
            ORDER BY f.created_at DESC, f.following_id DESC
            """)
    IPage<User> selectFollowing(Page<User> page, @Param("userId") Long userId);
}

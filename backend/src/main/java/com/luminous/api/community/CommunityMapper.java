package com.luminous.api.community;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

public interface CommunityMapper extends BaseMapper<Community> {

    @Update("UPDATE communities SET members_count = GREATEST(0, members_count + #{delta}), updated_at = NOW() WHERE id = #{communityId}")
    int updateMembersCountAtomic(@Param("communityId") Long communityId, @Param("delta") int delta);
}

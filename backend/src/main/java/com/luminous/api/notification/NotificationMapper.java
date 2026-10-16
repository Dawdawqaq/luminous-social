package com.luminous.api.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Update("UPDATE notifications SET read_at=CURRENT_TIMESTAMP(6),read_version=interaction_count,is_read=true WHERE receiver_id=#{receiverId} AND deleted_at IS NULL AND is_read=false")
    int markAllAsRead(@Param("receiverId") Long receiverId);
}

package com.luminous.api.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface NotificationMapper extends BaseMapper<Notification> {

    @Update("UPDATE notifications SET is_read = true WHERE receiver_id = #{receiverId} AND is_read = false")
    int markAllAsRead(@Param("receiverId") Long receiverId);
}

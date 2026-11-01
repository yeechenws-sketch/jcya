package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.HealthAlerts;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface HealthAlertsMapper {
    int insert(HealthAlerts record);
    HealthAlerts selectById(Long id);
    List<HealthAlerts> selectByUserId(@Param("userId") Long userId, @Param("limit") int limit);
    List<HealthAlerts> selectUnreadByUserId(Long userId);
    int updateReadStatus(@Param("id") Long id, @Param("isRead") Integer isRead);
    int updateReadStatusByUserId(Long userId);
}

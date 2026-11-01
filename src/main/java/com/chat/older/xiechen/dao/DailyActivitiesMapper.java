package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.DailyActivities;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface DailyActivitiesMapper {
    int insert(DailyActivities record);

    int update(DailyActivities record);

    DailyActivities selectById(Long id);

    DailyActivities selectByUserIdAndDate(@Param("userId") Long userId, @Param("recordDate") Date recordDate);

    List<DailyActivities> selectByUserIdAndDateRange(@Param("userId") Long userId, @Param("startDate") Date startDate, @Param("endDate") Date endDate);

    int upsert(DailyActivities record);
}

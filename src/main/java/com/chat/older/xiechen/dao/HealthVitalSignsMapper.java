package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.HealthVitalSigns;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface HealthVitalSignsMapper {
    int insert(HealthVitalSigns record);

    HealthVitalSigns selectById(Long id);

    List<HealthVitalSigns> selectByUserIdAndType(@Param("userId") Long userId, @Param("type") String type, @Param("limit") int limit);

    List<HealthVitalSigns> selectByUserIdAndTimeRange(@Param("userId") Long userId, @Param("startTime") Date startTime, @Param("endTime") Date endTime);

    List<HealthVitalSigns> selectLatestByUserId(@Param("userId") Long userId, @Param("types") List<String> types);

    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}

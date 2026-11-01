package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.HealthConfigLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface HealthConfigLogMapper {

    @Insert("INSERT INTO health_config_log (user_id, user_name, operator_id, operator_name, operator_type, " +
            "config_field, old_value, new_value, change_description, create_time) " +
            "VALUES (#{userId}, #{userName}, #{operatorId}, #{operatorName}, #{operatorType}, " +
            "#{configField}, #{oldValue}, #{newValue}, #{changeDescription}, NOW())")
    int insert(HealthConfigLog log);

    @Select("SELECT * FROM health_config_log WHERE user_id = #{userId} ORDER BY create_time DESC LIMIT #{limit}")
    List<HealthConfigLog> selectByUserId(@Param("userId") Long userId, @Param("limit") int limit);

    @Select("SELECT * FROM health_config_log ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<HealthConfigLog> selectAll(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM health_config_log")
    int countAll();

    @Select("SELECT COUNT(*) FROM health_config_log WHERE user_id = #{userId}")
    int countByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM health_config_log WHERE create_time >= #{startTime} AND create_time <= #{endTime} " +
            "ORDER BY create_time DESC")
    List<HealthConfigLog> selectByTimeRange(@Param("startTime") String startTime, @Param("endTime") String endTime);

    @Select("SELECT * FROM health_config_log WHERE operator_type = #{operatorType} " +
            "ORDER BY create_time DESC LIMIT #{limit}")
    List<HealthConfigLog> selectByOperatorType(@Param("operatorType") String operatorType, @Param("limit") int limit);
}

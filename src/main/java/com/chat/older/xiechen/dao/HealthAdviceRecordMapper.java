package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.HealthAdviceRecord;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface HealthAdviceRecordMapper {

    @Insert("INSERT INTO health_advice_record (user_id, user_name, admin_id, admin_name, advice_type, " +
            "advice_content, suggested_values, status, create_time) " +
            "VALUES (#{userId}, #{userName}, #{adminId}, #{adminName}, #{adviceType}, " +
            "#{adviceContent}, #{suggestedValues}, #{status}, NOW())")
    int insert(HealthAdviceRecord record);

    @Select("SELECT * FROM health_advice_record WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<HealthAdviceRecord> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM health_advice_record ORDER BY create_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<HealthAdviceRecord> selectAll(@Param("limit") int limit, @Param("offset") int offset);

    @Update("UPDATE health_advice_record SET status = #{status} WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);
}

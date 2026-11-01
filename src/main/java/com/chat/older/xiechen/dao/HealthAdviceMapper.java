package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.HealthAdvice;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HealthAdviceMapper {
    
    @Select("SELECT id, advice_type, title, content, priority, is_active, create_time, update_time " +
            "FROM health_advices WHERE is_active = 1 ORDER BY priority ASC, id ASC")
    List<HealthAdvice> selectAllActive();

    @Select("SELECT id, advice_type, title, content, priority, is_active, create_time, update_time " +
            "FROM health_advices WHERE is_active = 1 AND advice_type = #{adviceType} ORDER BY priority ASC")
    List<HealthAdvice> selectByType(String adviceType);

    @Select("SELECT id, advice_type, title, content, priority, is_active, create_time, update_time " +
            "FROM health_advices WHERE is_active = 1 ORDER BY RAND() LIMIT #{limit}")
    List<HealthAdvice> selectRandom(int limit);
}

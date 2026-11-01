package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.EmergencyCallRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface EmergencyCallRecordMapper {
    
    int insert(EmergencyCallRecord record);
    
    int update(EmergencyCallRecord record);
    
    EmergencyCallRecord selectById(@Param("id") Long id);
    
    EmergencyCallRecord selectActiveByElderId(@Param("elderUserId") Long elderUserId);
    
    List<EmergencyCallRecord> selectByElderId(@Param("elderUserId") Long elderUserId);
    
    List<EmergencyCallRecord> selectAll();
}

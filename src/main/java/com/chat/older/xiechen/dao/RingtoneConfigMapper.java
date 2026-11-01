package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.RingtoneConfig;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface RingtoneConfigMapper {
    
    @Select("SELECT * FROM ringtone_config ORDER BY id")
    List<RingtoneConfig> selectAll();
    
    @Select("SELECT * FROM ringtone_config WHERE type = #{type}")
    RingtoneConfig selectByType(@Param("type") String type);
    
    @Update("UPDATE ringtone_config SET url = #{url}, enabled = #{enabled}, update_time = NOW() WHERE type = #{type}")
    int update(RingtoneConfig config);
    
    @Insert("INSERT INTO ringtone_config (type, name, url, enabled, update_time) VALUES (#{type}, #{name}, #{url}, #{enabled}, NOW()) ON DUPLICATE KEY UPDATE url = #{url}, enabled = #{enabled}, update_time = NOW()")
    int insertOrUpdate(RingtoneConfig config);
    
    @Delete("DELETE FROM ringtone_config WHERE type = #{type}")
    int deleteByType(@Param("type") String type);
}

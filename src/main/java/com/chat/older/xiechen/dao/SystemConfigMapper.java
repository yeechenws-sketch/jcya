package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.SystemConfig;
import org.apache.ibatis.annotations.*;

@Mapper
public interface SystemConfigMapper {
    
    @Select("SELECT * FROM system_config WHERE config_key = #{key}")
    SystemConfig getByKey(String key);
    
    @Select("SELECT config_value FROM system_config WHERE config_key = #{key}")
    String getValueByKey(String key);
    
    @Insert("INSERT INTO system_config (config_key, config_value, description, updated_at) " +
            "VALUES (#{configKey}, #{configValue}, #{description}, NOW()) " +
            "ON DUPLICATE KEY UPDATE config_value = #{configValue}, updated_at = NOW()")
    int insertOrUpdate(SystemConfig config);
    
    @Update("UPDATE system_config SET config_value = #{value}, updated_at = NOW() WHERE config_key = #{key}")
    int updateValue(@Param("key") String key, @Param("value") String value);
}

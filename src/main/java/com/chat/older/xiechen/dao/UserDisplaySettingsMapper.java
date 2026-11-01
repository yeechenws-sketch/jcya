package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.UserDisplaySettings;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserDisplaySettingsMapper {
    
    @Select("SELECT * FROM user_display_settings WHERE user_id = #{userId}")
    UserDisplaySettings findByUserId(Long userId);
    
    @Insert("INSERT INTO user_display_settings (user_id, display_size, volume, voice_mode) VALUES (#{userId}, #{displaySize}, #{volume}, #{voiceMode})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserDisplaySettings settings);
    
    @Update("UPDATE user_display_settings SET display_size = #{displaySize}, volume = #{volume}, voice_mode = #{voiceMode} WHERE user_id = #{userId}")
    int update(UserDisplaySettings settings);
    
    @Delete("DELETE FROM user_display_settings WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);
}

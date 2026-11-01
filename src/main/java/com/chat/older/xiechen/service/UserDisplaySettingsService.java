package com.chat.older.xiechen.service;

import com.chat.older.xiechen.model.UserDisplaySettings;

public interface UserDisplaySettingsService {
    
    UserDisplaySettings getSettingsByUserId(Long userId);
    
    void saveSettings(UserDisplaySettings settings);
    
    void updateSettings(UserDisplaySettings settings);
}

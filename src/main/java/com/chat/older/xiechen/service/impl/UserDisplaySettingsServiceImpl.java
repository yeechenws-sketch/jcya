package com.chat.older.xiechen.service.impl;

import com.chat.older.xiechen.dao.UserDisplaySettingsMapper;
import com.chat.older.xiechen.model.UserDisplaySettings;
import com.chat.older.xiechen.service.UserDisplaySettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserDisplaySettingsServiceImpl implements UserDisplaySettingsService {
    
    @Autowired
    private UserDisplaySettingsMapper settingsMapper;
    
    @Override
    public UserDisplaySettings getSettingsByUserId(Long userId) {
        return settingsMapper.findByUserId(userId);
    }
    
    @Override
    public void saveSettings(UserDisplaySettings settings) {
        UserDisplaySettings existing = settingsMapper.findByUserId(settings.getUserId());
        if (existing != null) {
            settingsMapper.update(settings);
        } else {
            settingsMapper.insert(settings);
        }
    }
    
    @Override
    public void updateSettings(UserDisplaySettings settings) {
        UserDisplaySettings existing = settingsMapper.findByUserId(settings.getUserId());
        if (existing != null) {
            settingsMapper.update(settings);
        } else {
            settingsMapper.insert(settings);
        }
    }
}

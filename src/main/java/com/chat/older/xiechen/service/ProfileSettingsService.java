package com.chat.older.xiechen.service;

import com.chat.older.xiechen.model.dto.ProfileSettingsDTO;
import com.chat.older.xiechen.vo.ProfileSettingsVO;

public interface ProfileSettingsService {

    ProfileSettingsVO getSettings(Long userId);


    void saveProfile(Long userId, ProfileSettingsDTO dto);
}

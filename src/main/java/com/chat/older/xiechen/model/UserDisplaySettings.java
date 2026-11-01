package com.chat.older.xiechen.model;

import java.time.LocalDateTime;

public class UserDisplaySettings {
    private Long id;
    private Long userId;
    private Integer displaySize;
    private Integer volume;
    private Integer voiceMode;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public UserDisplaySettings() {
    }

    public UserDisplaySettings(Long userId, Integer displaySize, Integer volume, Integer voiceMode) {
        this.userId = userId;
        this.displaySize = displaySize;
        this.volume = volume;
        this.voiceMode = voiceMode;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Integer getDisplaySize() {
        return displaySize;
    }

    public void setDisplaySize(Integer displaySize) {
        this.displaySize = displaySize;
    }

    public Integer getVolume() {
        return volume;
    }

    public void setVolume(Integer volume) {
        this.volume = volume;
    }

    public Integer getVoiceMode() {
        return voiceMode;
    }

    public void setVoiceMode(Integer voiceMode) {
        this.voiceMode = voiceMode;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }
}

package com.chat.older.xiechen.service;

public interface AlertService {
    void processAlert(Long userId, String type, Float value1, Float value2, Long vitalSignId);
}

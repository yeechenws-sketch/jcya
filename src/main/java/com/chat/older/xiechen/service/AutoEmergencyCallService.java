package com.chat.older.xiechen.service;

import com.chat.older.xiechen.model.EmergencyCallRecord;
import java.util.Map;

public interface AutoEmergencyCallService {
    Map<String, Object> triggerAutoEmergencyCall(Long userId, String alertType, Float value1, Float value2, Integer alertLevel);
    Map<String, Object> getAutoCallStatus(Long userId);
    void cancelAutoCall(Long userId);
    boolean answerAutoCall(Long callRecordId, Long answeredByUserId);
}

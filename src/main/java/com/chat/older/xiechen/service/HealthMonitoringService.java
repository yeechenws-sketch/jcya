package com.chat.older.xiechen.service;

import com.chat.older.xiechen.model.HealthVitalSigns;
import com.chat.older.xiechen.model.DailyActivities;
import com.chat.older.xiechen.vo.HealthCardVO;
import com.chat.older.xiechen.vo.DailyTrendVO;
import java.util.Date;
import java.util.List;

public interface HealthMonitoringService {
    void processVitalSignsData(Long userId, String type, Float value1, Float value2, String deviceId);
    void processActivityData(Long userId, Integer steps, Float sleepHours);
    List<HealthCardVO> getHealthCards(Long userId);
    DailyTrendVO getDailyTrend(Long userId, int days);
    HealthVitalSigns getLatestVitalSign(Long userId, String type);
    List<HealthVitalSigns> getVitalSignsHistory(Long userId, String type, int limit);
}

package com.chat.older.xiechen.service.impl;

import com.chat.older.xiechen.dao.HealthAlertsMapper;
import com.chat.older.xiechen.dao.UserHealthConfigMapper;
import com.chat.older.xiechen.dao.UserMapper;
import com.chat.older.xiechen.model.HealthAlerts;
import com.chat.older.xiechen.model.UserHealthConfig;
import com.chat.older.xiechen.service.AlertService;
import com.chat.older.xiechen.service.AutoEmergencyCallService;
import com.chat.older.xiechen.handler.ChatHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Service
public class AlertServiceImpl implements AlertService {

    @Autowired
    private HealthAlertsMapper healthAlertsMapper;

    @Autowired
    private UserHealthConfigMapper userHealthConfigMapper;

    @Autowired
    private AutoEmergencyCallService autoEmergencyCallService;

    @Autowired
    private ChatHandler chatHandler;

    @Autowired
    private UserMapper userMapper;

    @Override
    public void processAlert(Long userId, String type, Float value1, Float value2, Long vitalSignId) {
        UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
        if (config == null) {
            return;
        }

        int alertLevel = 0;
        float thresholdValue = 0;

        switch (type) {
            case "BP":
                alertLevel = calculateBPAlertLevel(config, value1, value2);
                thresholdValue = Math.max(config.getBpSysMax(), config.getBpDiaMax());
                break;
            case "HR":
                alertLevel = calculateHRAlertLevel(config, value1);
                thresholdValue = Math.max(config.getHrMax(), config.getHrMin());
                break;
            case "BO":
                alertLevel = calculateBOAlertLevel(config, value1);
                thresholdValue = config.getBoMin();
                break;
            case "BG":
                alertLevel = calculateBGAlertLevel(config, value1);
                thresholdValue = config.getBgFastingMax();
                break;
        }

        if (alertLevel > 0) {
            HealthAlerts alert = new HealthAlerts();
            alert.setUserId(userId);
            alert.setAlertType(type);
            alert.setAlertLevel(alertLevel);
            alert.setCurrentValue(value1);
            alert.setThresholdValue(thresholdValue);
            alert.setVitalSignId(vitalSignId);
            alert.setIsRead(0);
            alert.setCreateTime(new Date());

            healthAlertsMapper.insert(alert);
            if (alertLevel == 3) {
                triggerEmergencyCall(userId, type, value1, value2);
            }
        }
    }

    private int calculateBPAlertLevel(UserHealthConfig config, Float systolic, Float diastolic) {
        if (systolic > config.getBpSysEmergency() || diastolic > config.getBpDiaEmergency()) {
            return 3; // 严重
        } else if (systolic > config.getBpSysMax() || diastolic > config.getBpDiaMax()) {
            return 2; // 中度
        } else if (systolic < config.getBpSysMin() || diastolic < config.getBpDiaMin()) {
            return 1; // 警告
        }
        return 0;
    }

    private int calculateHRAlertLevel(UserHealthConfig config, Float heartRate) {
        if (heartRate > config.getHrMax() * 1.5 || heartRate < config.getHrMin() * 0.5) {
            return 3; // 严重
        } else if (heartRate > config.getHrMax() * 1.2 || heartRate < config.getHrMin() * 0.8) {
            return 2; // 中度
        } else if (heartRate > config.getHrMax() || heartRate < config.getHrMin()) {
            return 1; // 警告
        }
        return 0;
    }

    private int calculateBOAlertLevel(UserHealthConfig config, Float bloodOxygen) {
        if (bloodOxygen < config.getBoWarning()) {
            return 3; // 严重
        } else if (bloodOxygen < config.getBoMin()) {
            return 1; // 警告
        }
        return 0;
    }

    private int calculateBGAlertLevel(UserHealthConfig config, Float bloodGlucose) {
        if (bloodGlucose > config.getBgFastingMax() * 1.5 || bloodGlucose < config.getBgFastingMin() * 0.5) {
            return 3; // 严重
        } else if (bloodGlucose > config.getBgFastingMax() * 1.2 || bloodGlucose < config.getBgFastingMin() * 0.8) {
            return 2; // 中度
        } else if (bloodGlucose > config.getBgFastingMax() || bloodGlucose < config.getBgFastingMin()) {
            return 1; // 警告
        }
        return 0;
    }

    private void triggerEmergencyCall(Long userId, String type, Float value1, Float value2) {
        System.out.println("紧急呼叫触发：用户 " + userId + " 的 " + type + " 数据异常，值为 " + value1 + (value2 != null ? "/" + value2 : ""));

        try {
            Map<String, Object> notice = new HashMap<>();
            notice.put("msgType", "AUTO_EMERGENCY_TRIGGERED");
            notice.put("alertType", type);
            notice.put("alertLevel", 3);
            notice.put("alertValue", value1 != null ? value1 : value2);
            notice.put("timestamp", System.currentTimeMillis());
            chatHandler.sendToUser(userId, notice);

            System.out.println("紧急呼叫通知已发送给用户: " + userId);
        } catch (Exception e) {
            System.err.println("发送紧急呼叫通知失败: " + e.getMessage());
        }
    }
}

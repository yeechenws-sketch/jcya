package com.chat.older.xiechen.service.impl;

import com.chat.older.xiechen.dao.MqttSimulationConfigMapper;
import com.chat.older.xiechen.model.MqttSimulationConfig;
import com.chat.older.xiechen.model.HealthAlerts;
import com.chat.older.xiechen.model.HealthVitalSigns;
import com.chat.older.xiechen.model.DailyActivities;
import com.chat.older.xiechen.dao.HealthVitalSignsMapper;
import com.chat.older.xiechen.dao.DailyActivitiesMapper;
import com.chat.older.xiechen.dao.UserHealthConfigMapper;
import com.chat.older.xiechen.dao.HealthAlertsMapper;
import com.chat.older.xiechen.model.UserHealthConfig;
import com.chat.older.xiechen.service.MqttSimulationService;
import com.chat.older.xiechen.service.AlertService;
import com.chat.older.xiechen.controller.WebSocketController;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Service
public class MqttSimulationServiceImpl implements MqttSimulationService {

    @Autowired
    private MqttSimulationConfigMapper configMapper;
    
    @Autowired
    private HealthVitalSignsMapper healthVitalSignsMapper;
    
    @Autowired
    private DailyActivitiesMapper dailyActivitiesMapper;
    
    @Autowired
    private UserHealthConfigMapper userHealthConfigMapper;
    
    @Autowired
    private HealthAlertsMapper healthAlertsMapper;
    
    @Autowired
    private AlertService alertService;

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(10);
    private final Map<Long, ScheduledFuture<?>> simulationTasks = new ConcurrentHashMap<>();
    private final Map<Long, Map<String, Object>> simulationStatus = new ConcurrentHashMap<>();
    private final Gson gson = new Gson();

    @Override
    public Map<String, Object> simulateData(Long userId, String dataType, Float value1, Float value2) {
        Map<String, Object> result = new HashMap<>();
        result.put("dataType", dataType);
        result.put("value1", value1);
        result.put("value2", value2);
        result.put("timestamp", System.currentTimeMillis());
        
        try {
            String deviceId = "SIMULATOR_" + System.currentTimeMillis();
            
            if ("BP".equals(dataType) || "HR".equals(dataType) || "BO".equals(dataType) || "BG".equals(dataType)) {
                HealthVitalSigns vitalSigns = new HealthVitalSigns();
                vitalSigns.setUserId(userId);
                vitalSigns.setType(dataType);
                vitalSigns.setValue1(value1);
                vitalSigns.setValue2(value2);
                vitalSigns.setDeviceId(deviceId);
                vitalSigns.setCreateTime(new Date());
                vitalSigns.setUpdateTime(new Date());
                
                int status = checkVitalSignStatus(userId, dataType, value1, value2);
                vitalSigns.setStatus(status);
                
                healthVitalSignsMapper.insert(vitalSigns);
                result.put("recordId", vitalSigns.getId());
                result.put("status", status == 0 ? "正常" : "异常");
                
                if (status == 1) {
                    alertService.processAlert(userId, dataType, value1, value2, vitalSigns.getId());
                    result.put("alertTriggered", true);
                    
                    int alertLevel = determineAlertLevel(userId, dataType, value1, value2);
                    result.put("alertLevel", alertLevel);
                    result.put("alertLevelText", getAlertLevelText(alertLevel));
                }
            } else if ("STEPS".equals(dataType)) {
                Date today = getTodayDate();
                DailyActivities activity = dailyActivitiesMapper.selectByUserIdAndDate(userId, today);
                if (activity == null) {
                    activity = new DailyActivities();
                    activity.setUserId(userId);
                    activity.setRecordDate(today);
                    activity.setSteps(value1 != null ? value1.intValue() : 0);
                    activity.setUpdateTime(new Date());
                    dailyActivitiesMapper.insert(activity);
                } else {
                    activity.setSteps(value1 != null ? value1.intValue() : 0);
                    activity.setUpdateTime(new Date());
                    dailyActivitiesMapper.update(activity);
                }
                result.put("recordId", activity.getId());
                result.put("status", "已记录");
            } else if ("SLEEP".equals(dataType)) {
                Calendar cal = Calendar.getInstance();
                cal.add(Calendar.DAY_OF_YEAR, -1);
                Date yesterday = cal.getTime();
                
                DailyActivities activity = dailyActivitiesMapper.selectByUserIdAndDate(userId, yesterday);
                if (activity == null) {
                    activity = new DailyActivities();
                    activity.setUserId(userId);
                    activity.setRecordDate(yesterday);
                    activity.setSteps(0);
                    activity.setSleepHours(value1);
                    activity.setUpdateTime(new Date());
                    dailyActivitiesMapper.insert(activity);
                } else {
                    activity.setSleepHours(value1);
                    activity.setUpdateTime(new Date());
                    dailyActivitiesMapper.update(activity);
                }
                result.put("recordId", activity.getId());
                result.put("status", "已记录");
            }
            
            pushLatestDataToFrontend(userId);
            
            result.put("success", true);
            result.put("message", "数据模拟成功");
            
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "数据模拟失败: " + e.getMessage());
        }
        
        return result;
    }

    @Override
    public MqttSimulationConfig saveConfig(MqttSimulationConfig config) {
        if (config.getId() == null) {
            config.setAutoSend(0);
            config.setIsEnabled(1);
            configMapper.insert(config);
        } else {
            configMapper.update(config);
        }
        return config;
    }

    @Override
    public List<MqttSimulationConfig> getConfigs(Long userId) {
        return configMapper.selectByUserId(userId);
    }

    @Override
    public boolean deleteConfig(Long id) {
        return configMapper.delete(id) > 0;
    }

    @Override
    public boolean startAutoSimulation(Long userId) {
        if (simulationTasks.containsKey(userId)) {
            return false;
        }
        
        Map<String, Object> status = new HashMap<>();
        status.put("running", true);
        status.put("startTime", System.currentTimeMillis());
        status.put("dataCount", 0);
        simulationStatus.put(userId, status);
        
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                simulateRandomData(userId);
                Map<String, Object> s = simulationStatus.get(userId);
                if (s != null) {
                    s.put("dataCount", (Integer) s.get("dataCount") + 1);
                    s.put("lastUpdateTime", System.currentTimeMillis());
                }
            } catch (Exception e) {
                System.err.println("自动模拟数据失败: " + e.getMessage());
            }
        }, 0, 5, TimeUnit.SECONDS);
        
        simulationTasks.put(userId, future);
        return true;
    }

    @Override
    public boolean stopAutoSimulation(Long userId) {
        ScheduledFuture<?> future = simulationTasks.remove(userId);
        if (future != null) {
            future.cancel(false);
            Map<String, Object> status = simulationStatus.get(userId);
            if (status != null) {
                status.put("running", false);
                status.put("stopTime", System.currentTimeMillis());
            }
            return true;
        }
        return false;
    }

    @Override
    public boolean isSimulationRunning(Long userId) {
        ScheduledFuture<?> future = simulationTasks.get(userId);
        return future != null && !future.isCancelled() && !future.isDone();
    }

    @Override
    public Map<String, Object> getSimulationStatus(Long userId) {
        Map<String, Object> status = simulationStatus.get(userId);
        if (status == null) {
            status = new HashMap<>();
            status.put("running", false);
        }
        status.put("isRunning", isSimulationRunning(userId));
        return status;
    }
    
    private void simulateRandomData(Long userId) {
        Random random = new Random();
        int type = random.nextInt(6);
        
        switch (type) {
            case 0:
                int systolic = 110 + random.nextInt(40);
                int diastolic = 70 + random.nextInt(30);
                simulateData(userId, "BP", (float) systolic, (float) diastolic);
                break;
            case 1:
                int hr = 60 + random.nextInt(50);
                simulateData(userId, "HR", (float) hr, null);
                break;
            case 2:
                int bo = 94 + random.nextInt(6);
                simulateData(userId, "BO", (float) bo, null);
                break;
            case 3:
                float bg = 4.0f + random.nextFloat() * 5.0f;
                simulateData(userId, "BG", bg, null);
                break;
            case 4:
                int steps = 1000 + random.nextInt(8000);
                simulateData(userId, "STEPS", (float) steps, null);
                break;
            case 5:
                float sleep = 5.0f + random.nextFloat() * 4.0f;
                simulateData(userId, "SLEEP", sleep, null);
                break;
        }
    }
    
    private int checkVitalSignStatus(Long userId, String type, Float value1, Float value2) {
        UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
        if (config == null) {
            config = createDefaultHealthConfig(userId);
        }

        switch (type) {
            case "BP":
                return checkBloodPressureStatus(config, value1, value2);
            case "HR":
                return checkHeartRateStatus(config, value1);
            case "BO":
                return checkBloodOxygenStatus(config, value1);
            case "BG":
                return checkBloodGlucoseStatus(config, value1);
            default:
                return 0;
        }
    }
    
    private int checkBloodPressureStatus(UserHealthConfig config, Float systolic, Float diastolic) {
        if (systolic > config.getBpSysMax() || diastolic > config.getBpDiaMax()) {
            return 1;
        }
        if (config.getBpSysMin() != null && (systolic < config.getBpSysMin() || diastolic < config.getBpDiaMin())) {
            return 1;
        }
        return 0;
    }

    private int checkHeartRateStatus(UserHealthConfig config, Float heartRate) {
        if (heartRate > config.getHrMax() || heartRate < config.getHrMin()) {
            return 1;
        }
        return 0;
    }

    private int checkBloodOxygenStatus(UserHealthConfig config, Float bloodOxygen) {
        if (bloodOxygen < config.getBoMin()) {
            return 1;
        }
        return 0;
    }

    private int checkBloodGlucoseStatus(UserHealthConfig config, Float bloodGlucose) {
        if (bloodGlucose > config.getBgFastingMax() || bloodGlucose < config.getBgFastingMin()) {
            return 1;
        }
        return 0;
    }
    
    private int determineAlertLevel(Long userId, String type, Float value1, Float value2) {
        UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
        if (config == null) return 1;
        
        switch (type) {
            case "BP":
                if (value1 >= config.getBpSysEmergency() || value2 >= config.getBpDiaEmergency()) {
                    return 3;
                } else if (value1 > config.getBpSysMax() + 20 || value2 > config.getBpDiaMax() + 15) {
                    return 2;
                }
                return 1;
            case "HR":
                if (value1 > config.getHrMax() + 30 || value1 < config.getHrMin() - 20) {
                    return 3;
                } else if (value1 > config.getHrMax() + 15 || value1 < config.getHrMin() - 10) {
                    return 2;
                }
                return 1;
            case "BO":
                if (value1 < 90) return 3;
                if (value1 < 92) return 2;
                return 1;
            case "BG":
                if (value1 > 15 || value1 < 2.8) return 3;
                if (value1 > 11 || value1 < 3.5) return 2;
                return 1;
            default:
                return 1;
        }
    }
    
    private String getAlertLevelText(int level) {
        switch (level) {
            case 1: return "警告";
            case 2: return "中度";
            case 3: return "严重";
            default: return "未知";
        }
    }
    
    private UserHealthConfig createDefaultHealthConfig(Long userId) {
        UserHealthConfig config = new UserHealthConfig();
        config.setUserId(userId);
        config.setHrMin(60);
        config.setHrMax(100);
        config.setBoMin(95);
        config.setBoWarning(98);
        config.setBpSysMin(90);
        config.setBpSysMax(140);
        config.setBpSysEmergency(180);
        config.setBpDiaMin(60);
        config.setBpDiaMax(90);
        config.setBpDiaEmergency(120);
        config.setBgFastingMin(3.9f);
        config.setBgFastingMax(6.1f);
        config.setBgAfterMealMin(3.9f);
        config.setBgAfterMealMax(7.8f);
        config.setStepTarget(6000);
        config.setSleepTarget(7.5f);
        config.setEnableNotifications(1);
        config.setUpdateTime(new Date());
        
        try {
            userHealthConfigMapper.insert(config);
        } catch (Exception e) {
            System.err.println("创建默认健康配置失败: " + e.getMessage());
        }
        
        return config;
    }
    
    private Date getTodayDate() {
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        return calendar.getTime();
    }
    
    private void pushLatestDataToFrontend(Long userId) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("type", "mqtt_simulation_update");
            message.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = gson.toJson(message);
            WebSocketController.sendMessageToUser(userId, jsonMessage);
        } catch (Exception e) {
            System.err.println("推送模拟数据通知失败: " + e.getMessage());
        }
    }
}

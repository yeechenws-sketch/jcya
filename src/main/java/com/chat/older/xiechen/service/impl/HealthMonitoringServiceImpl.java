package com.chat.older.xiechen.service.impl;

import com.chat.older.xiechen.dao.HealthVitalSignsMapper;
import com.chat.older.xiechen.dao.DailyActivitiesMapper;
import com.chat.older.xiechen.dao.UserHealthConfigMapper;
import com.chat.older.xiechen.model.HealthVitalSigns;
import com.chat.older.xiechen.model.DailyActivities;
import com.chat.older.xiechen.model.UserHealthConfig;
import com.chat.older.xiechen.service.HealthMonitoringService;
import com.chat.older.xiechen.service.AlertService;
import com.chat.older.xiechen.vo.HealthCardVO;
import com.chat.older.xiechen.vo.DailyTrendVO;
import com.chat.older.xiechen.controller.WebSocketController;
import com.google.gson.Gson;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.*;

@Service
public class HealthMonitoringServiceImpl implements HealthMonitoringService {

    @Autowired
    private HealthVitalSignsMapper healthVitalSignsMapper;

    @Autowired
    private DailyActivitiesMapper dailyActivitiesMapper;

    @Autowired
    private UserHealthConfigMapper userHealthConfigMapper;

    @Autowired
    private AlertService alertService;

    @Override
    public void processVitalSignsData(Long userId, String type, Float value1, Float value2, String deviceId) {
        System.out.println("Processing vital signs data: userId=" + userId + ", type=" + type + ", value1=" + value1 + ", value2=" + value2);
        
        HealthVitalSigns vitalSigns = new HealthVitalSigns();
        vitalSigns.setUserId(userId);
        vitalSigns.setType(type);
        vitalSigns.setValue1(value1);
        vitalSigns.setValue2(value2);
        vitalSigns.setDeviceId(deviceId);
        vitalSigns.setCreateTime(new Date());
        vitalSigns.setUpdateTime(new Date());

        int status = checkVitalSignStatus(userId, type, value1, value2);
        vitalSigns.setStatus(status);
        System.out.println("Vital signs status: " + status);

        System.out.println("Saving vital signs data to database...");
        healthVitalSignsMapper.insert(vitalSigns);
        System.out.println("Vital signs data saved successfully, id=" + vitalSigns.getId());

        if (status == 1) {
            System.out.println("Triggering alert for abnormal status...");
            alertService.processAlert(userId, type, value1, value2, vitalSigns.getId());
        }

        // 通过WebSocket推送最新数据给前端
        System.out.println("Pushing latest data to frontend via WebSocket...");
        pushLatestDataToFrontend(userId);
        System.out.println("Data pushed to frontend successfully");
    }

    @Override
    public void processActivityData(Long userId, Integer steps, Float sleepHours) {
        System.out.println("Processing activity data: userId=" + userId + ", steps=" + steps + ", sleepHours=" + sleepHours);
        
        Date today = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);

        if (steps != null) {
            Date stepsDate = calendar.getTime();
            System.out.println("Steps record date: " + stepsDate);
            
            DailyActivities stepsActivity = dailyActivitiesMapper.selectByUserIdAndDate(userId, stepsDate);
            if (stepsActivity == null) {
                System.out.println("Creating new steps record...");
                stepsActivity = new DailyActivities();
                stepsActivity.setUserId(userId);
                stepsActivity.setRecordDate(stepsDate);
                stepsActivity.setSteps(steps);
                stepsActivity.setSleepHours(null); // 睡眠数据可能为null
                stepsActivity.setUpdateTime(new Date());
                dailyActivitiesMapper.insert(stepsActivity);
                System.out.println("New steps record created");
            } else {
                System.out.println("Updating existing steps record...");
                stepsActivity.setSteps(steps);
                stepsActivity.setUpdateTime(new Date());
                dailyActivitiesMapper.update(stepsActivity);
                System.out.println("Steps record updated");
            }
        }

        if (sleepHours != null) {
            calendar.add(Calendar.DAY_OF_YEAR, -1); // 减去1天，变成昨天
            Date sleepDate = calendar.getTime();
            System.out.println("Sleep record date: " + sleepDate);
            
            DailyActivities sleepActivity = dailyActivitiesMapper.selectByUserIdAndDate(userId, sleepDate);
            if (sleepActivity == null) {
                System.out.println("Creating new sleep record...");
                sleepActivity = new DailyActivities();
                sleepActivity.setUserId(userId);
                sleepActivity.setRecordDate(sleepDate);
                sleepActivity.setSteps(0); // 步数设置为0，避免null错误
                sleepActivity.setSleepHours(sleepHours);
                sleepActivity.setUpdateTime(new Date());
                dailyActivitiesMapper.insert(sleepActivity);
                System.out.println("New sleep record created");
            } else {
                System.out.println("Updating existing sleep record...");
                sleepActivity.setSleepHours(sleepHours);
                sleepActivity.setUpdateTime(new Date());
                dailyActivitiesMapper.update(sleepActivity);
                System.out.println("Sleep record updated");
            }
        }

        System.out.println("Pushing activity data to frontend via WebSocket...");
        pushLatestDataToFrontend(userId);
        System.out.println("Activity data pushed to frontend successfully");
    }

    @Override
    public List<HealthCardVO> getHealthCards(Long userId) {
        List<HealthCardVO> cards = new ArrayList<>();
        List<String> types = Arrays.asList("BP", "HR", "BO", "BG");
        List<HealthVitalSigns> latestVitalSigns = healthVitalSignsMapper.selectLatestByUserId(userId, types);
        Map<String, HealthVitalSigns> vitalSignsMap = new HashMap<>();
        for (HealthVitalSigns sign : latestVitalSigns) {
            vitalSignsMap.put(sign.getType(), sign);
        }
        HealthCardVO bpCard = createBPCard(vitalSignsMap.get("BP"));
        cards.add(bpCard);
        HealthCardVO hrCard = createHRCard(vitalSignsMap.get("HR"));
        cards.add(hrCard);
        HealthCardVO boCard = createBOCard(vitalSignsMap.get("BO"));
        cards.add(boCard);
        HealthCardVO bgCard = createBGCard(vitalSignsMap.get("BG"));
        cards.add(bgCard);
        HealthCardVO stepsCard = createStepsCard(userId);
        cards.add(stepsCard);
        HealthCardVO sleepCard = createSleepCard(userId);
        cards.add(sleepCard);
        return cards;
    }

    @Override
    public DailyTrendVO getDailyTrend(Long userId, int days) {
        DailyTrendVO trendVO = new DailyTrendVO();
        List<String> dates = new ArrayList<>();
        List<Float> bloodPressure = new ArrayList<>();
        List<Float> heartRate = new ArrayList<>();
        List<Float> bloodOxygen = new ArrayList<>();
        List<Float> bloodGlucose = new ArrayList<>();
        List<Integer> steps = new ArrayList<>();
        List<Float> sleepHours = new ArrayList<>();
        Date endDate = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(endDate);
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        endDate = calendar.getTime();
        calendar.add(Calendar.DAY_OF_YEAR, -days + 1);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Date startDate = calendar.getTime();
        List<HealthVitalSigns> vitalSignsList = healthVitalSignsMapper.selectByUserIdAndTimeRange(userId, startDate, endDate);
        Map<String, Map<String, Float>> vitalSignsMap = new HashMap<>();
        for (HealthVitalSigns sign : vitalSignsList) {
            String dateStr = formatDate(sign.getCreateTime());
            String type = sign.getType();
            if (!vitalSignsMap.containsKey(dateStr)) {
                vitalSignsMap.put(dateStr, new HashMap<>());
            }
            if (type.equals("BP")) {
                vitalSignsMap.get(dateStr).put("BP", sign.getValue1());
            } else if (type.equals("HR")) {
                vitalSignsMap.get(dateStr).put("HR", sign.getValue1());
            } else if (type.equals("BO")) {
                vitalSignsMap.get(dateStr).put("BO", sign.getValue1());
            } else if (type.equals("BG")) {
                vitalSignsMap.get(dateStr).put("BG", sign.getValue1());
            }
        }

        List<DailyActivities> activitiesList = dailyActivitiesMapper.selectByUserIdAndDateRange(userId, startDate, endDate);
        Map<String, DailyActivities> activitiesMap = new HashMap<>();
        for (DailyActivities activity : activitiesList) {
            activitiesMap.put(formatDate(activity.getRecordDate()), activity);
        }

        Calendar tempCalendar = Calendar.getInstance();
        tempCalendar.setTime(startDate);
        
        for (int i = 0; i < days; i++) {
            Date currentDate = tempCalendar.getTime();
            String dateStr = formatDate(currentDate);
            dates.add(dateStr);

            Map<String, Float> dayVitalSigns = vitalSignsMap.getOrDefault(dateStr, new HashMap<>());
            bloodPressure.add(dayVitalSigns.getOrDefault("BP", 0f));
            heartRate.add(dayVitalSigns.getOrDefault("HR", 0f));
            bloodOxygen.add(dayVitalSigns.getOrDefault("BO", 0f));
            bloodGlucose.add(dayVitalSigns.getOrDefault("BG", 0f));

            DailyActivities activity = activitiesMap.get(dateStr);
            steps.add(activity != null ? activity.getSteps() : 0);
            sleepHours.add(activity != null && activity.getSleepHours() != null ? activity.getSleepHours() : 0f);
            
            tempCalendar.add(Calendar.DAY_OF_YEAR, 1);
        }

        trendVO.setDates(dates);
        trendVO.setBloodPressure(bloodPressure);
        trendVO.setHeartRate(heartRate);
        trendVO.setBloodOxygen(bloodOxygen);
        trendVO.setBloodGlucose(bloodGlucose);
        trendVO.setSteps(steps);
        trendVO.setSleepHours(sleepHours);

        return trendVO;
    }

    @Override
    public HealthVitalSigns getLatestVitalSign(Long userId, String type) {
        List<HealthVitalSigns> list = healthVitalSignsMapper.selectByUserIdAndType(userId, type, 1);
        return list != null && !list.isEmpty() ? list.get(0) : null;
    }

    @Override
    public List<HealthVitalSigns> getVitalSignsHistory(Long userId, String type, int limit) {
        return healthVitalSignsMapper.selectByUserIdAndType(userId, type, limit);
    }

    private int checkVitalSignStatus(Long userId, String type, Float value1, Float value2) {
        UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
        if (config == null) {
            // 创建默认健康配置
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
        config.setNotificationTime("08:00");
        config.setUpdateTime(new Date());
        try {
            userHealthConfigMapper.insert(config);
            System.out.println("Created default health config for userId: " + userId);
        } catch (Exception e) {
            System.err.println("Failed to create default health config: " + e.getMessage());
        }
        
        return config;
    }

    private int checkBloodPressureStatus(UserHealthConfig config, Float systolic, Float diastolic) {
        if (systolic > config.getBpSysMax() || diastolic > config.getBpDiaMax()) {
            return 1;
        }
        if (systolic < config.getBpSysMin() || diastolic < config.getBpDiaMin()) {
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

    private HealthCardVO createBPCard(HealthVitalSigns bpData) {
        HealthCardVO card = new HealthCardVO();
        card.setId(1);
        card.setLabel("血压 (高压/低压)");
        card.setIcon("fa-heartbeat.png");
        card.setColor("#4361EE");

        if (bpData != null) {
            card.setValue(bpData.getValue1() + "/" + (bpData.getValue2() != null ? bpData.getValue2() : "0"));
            card.setStatus(bpData.getStatus() == 1 ? "警告" : "正常");
        } else {
            card.setValue("--/--");
            card.setStatus("未测量");
        }
        card.setUnit("mmHg");
        return card;
    }

    private HealthCardVO createHRCard(HealthVitalSigns hrData) {
        HealthCardVO card = new HealthCardVO();
        card.setId(2);
        card.setLabel("心率");
        card.setIcon("fa-tachometer.png");
        card.setColor("#F72585");

        if (hrData != null) {
            card.setValue(hrData.getValue1().toString());
            card.setStatus(hrData.getStatus() == 1 ? "警告" : "正常");
        } else {
            card.setValue("--");
            card.setStatus("未测量");
        }
        card.setUnit("次/分");
        return card;
    }

    private HealthCardVO createBOCard(HealthVitalSigns boData) {
        HealthCardVO card = new HealthCardVO();
        card.setId(3);
        card.setLabel("血氧饱和度");
        card.setIcon("fa-leaf.png");
        card.setColor("#06D6A0");

        if (boData != null) {
            card.setValue(boData.getValue1().toString());
            card.setStatus(boData.getStatus() == 1 ? "警告" : "正常");
        } else {
            card.setValue("--");
            card.setStatus("未测量");
        }
        card.setUnit("%");
        return card;
    }

    private HealthCardVO createBGCard(HealthVitalSigns bgData) {
        HealthCardVO card = new HealthCardVO();
        card.setId(4);
        card.setLabel("血糖 (空腹)");
        card.setIcon("fa-tint.png");
        card.setColor("#FF9F1C");

        if (bgData != null) {
            card.setValue(bgData.getValue1().toString());
            
            UserHealthConfig config = userHealthConfigMapper.selectByUserId(bgData.getUserId());
            if (config == null) {
                config = createDefaultHealthConfig(bgData.getUserId());
            }
            
            if (bgData.getValue1() > config.getBgFastingMax()) {
                card.setStatus("稍高");
            } else if (bgData.getValue1() < config.getBgFastingMin()) {
                card.setStatus("偏低");
            } else {
                card.setStatus("正常");
            }
        } else {
            card.setValue("--");
            card.setStatus("未测量");
        }
        card.setUnit("mmol/L");
        return card;
    }

    private HealthCardVO createStepsCard(Long userId) {
        HealthCardVO card = new HealthCardVO();
        card.setId(5);
        card.setLabel("今日步数");
        card.setIcon("fa-blind.png");
        card.setColor("#7209B7");

        Date today = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(today);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Date recordDate = calendar.getTime();

        DailyActivities activity = dailyActivitiesMapper.selectByUserIdAndDate(userId, recordDate);
        if (activity != null) {
            card.setValue(activity.getSteps().toString());
            
            UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
            if (config == null) {
                config = createDefaultHealthConfig(userId);
            }
            if (activity.getSteps() >= config.getStepTarget()) {
                card.setStatus("达标");
            } else if (activity.getSteps() >= config.getStepTarget() * 0.8) {
                card.setStatus("接近目标");
            } else {
                card.setStatus("未达标");
            }
        } else {
            card.setValue("0");
            card.setStatus("未达标");
        }
        card.setUnit("步");
        return card;
    }

    private HealthCardVO createSleepCard(Long userId) {
        HealthCardVO card = new HealthCardVO();
        card.setId(6);
        card.setLabel("睡眠时长");
        card.setIcon("fa-moon-o.png");
        card.setColor("#4895EF");

        // 直接查询今天和昨天的记录，避免日期范围查询的时区问题
        System.out.println("查询睡眠数据: userId=" + userId);
        Date today = new Date();
        
        // 今天的记录
        Calendar todayCal = Calendar.getInstance();
        todayCal.setTime(today);
        todayCal.set(Calendar.HOUR_OF_DAY, 0);
        todayCal.set(Calendar.MINUTE, 0);
        todayCal.set(Calendar.SECOND, 0);
        Date todayDate = todayCal.getTime();
        System.out.println("今天日期: " + todayDate);

        Calendar yesterdayCal = Calendar.getInstance();
        yesterdayCal.setTime(today);
        yesterdayCal.add(Calendar.DAY_OF_YEAR, -1);
        yesterdayCal.set(Calendar.HOUR_OF_DAY, 0);
        yesterdayCal.set(Calendar.MINUTE, 0);
        yesterdayCal.set(Calendar.SECOND, 0);
        Date yesterdayDate = yesterdayCal.getTime();
        System.out.println("昨天日期: " + yesterdayDate);

        DailyActivities todayActivity = dailyActivitiesMapper.selectByUserIdAndDate(userId, todayDate);
        System.out.println("今天的记录: " + todayActivity);
        if (todayActivity != null && todayActivity.getSleepHours() != null) {
            System.out.println("今天有睡眠数据: " + todayActivity.getSleepHours());
            card.setValue(todayActivity.getSleepHours().toString());
            
            UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
            if (config == null) {
                config = createDefaultHealthConfig(userId);
            }
            if (todayActivity.getSleepHours() >= config.getSleepTarget()) {
                card.setStatus("优");
            } else if (todayActivity.getSleepHours() >= config.getSleepTarget() * 0.8) {
                card.setStatus("良好");
            } else {
                card.setStatus("不足");
            }
            System.out.println("使用今天的睡眠数据: " + todayActivity.getSleepHours());
            return card;
        }

        DailyActivities yesterdayActivity = dailyActivitiesMapper.selectByUserIdAndDate(userId, yesterdayDate);
        System.out.println("昨天的记录: " + yesterdayActivity);
        if (yesterdayActivity != null && yesterdayActivity.getSleepHours() != null) {
            System.out.println("昨天有睡眠数据: " + yesterdayActivity.getSleepHours());
            card.setValue(yesterdayActivity.getSleepHours().toString());
            
            UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
            if (config == null) {
                config = createDefaultHealthConfig(userId);
            }
            if (yesterdayActivity.getSleepHours() >= config.getSleepTarget()) {
                card.setStatus("优");
            } else if (yesterdayActivity.getSleepHours() >= config.getSleepTarget() * 0.8) {
                card.setStatus("良好");
            } else {
                card.setStatus("不足");
            }
            System.out.println("使用昨天的睡眠数据: " + yesterdayActivity.getSleepHours());
            return card;
        }

        System.out.println("未找到今天和昨天的睡眠数据，尝试查询最近7天的记录");
        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, 23);
        calendar.set(Calendar.MINUTE, 59);
        calendar.set(Calendar.SECOND, 59);
        Date endDate = calendar.getTime();
        
        calendar.add(Calendar.DAY_OF_YEAR, -7);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        Date startDate = calendar.getTime();
        
        System.out.println("查询日期范围: startDate=" + startDate + ", endDate=" + endDate);
        List<DailyActivities> activities = dailyActivitiesMapper.selectByUserIdAndDateRange(userId, startDate, endDate);
        System.out.println("查询结果数量: " + activities.size());
        
        DailyActivities latestActivity = null;
        Date latestDate = null;

        for (DailyActivities activity : activities) {
            System.out.println("记录: id=" + activity.getId() + ", 日期=" + activity.getRecordDate() + ", 睡眠时长=" + activity.getSleepHours() + ", 步数=" + activity.getSteps());
            if (activity.getSleepHours() != null) {
                if (latestDate == null || activity.getRecordDate().after(latestDate)) {
                    latestDate = activity.getRecordDate();
                    latestActivity = activity;
                }
            }
        }
        
        if (latestActivity != null) {
            System.out.println("找到最新睡眠数据: 日期=" + latestActivity.getRecordDate() + ", 时长=" + latestActivity.getSleepHours());
            card.setValue(latestActivity.getSleepHours().toString());
            
            UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
            if (config == null) {
                config = createDefaultHealthConfig(userId);
            }
            if (latestActivity.getSleepHours() >= config.getSleepTarget()) {
                card.setStatus("优");
            } else if (latestActivity.getSleepHours() >= config.getSleepTarget() * 0.8) {
                card.setStatus("良好");
            } else {
                card.setStatus("不足");
            }
            System.out.println("使用最近7天的睡眠数据: " + latestActivity.getSleepHours());
            return card;
        }

        System.out.println("未找到任何睡眠数据");
        card.setValue("--");
        card.setStatus("未记录");
        return card;
    }

    private String formatDate(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        int month = calendar.get(Calendar.MONTH) + 1;
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        return month + "-" + day;
    }

    private void pushLatestDataToFrontend(Long userId) {
        try {
            List<HealthCardVO> cards = getHealthCards(userId);

            Map<String, Object> message = new HashMap<>();
            message.put("type", "health_data_update");
            message.put("data", cards);
            message.put("timestamp", System.currentTimeMillis());
            Gson gson = new Gson();
            String jsonMessage = gson.toJson(message);
            WebSocketController.sendMessageToUser(userId, jsonMessage);
        } catch (Exception e) {
            System.err.println("推送健康数据失败: " + e.getMessage());
        }
    }
}

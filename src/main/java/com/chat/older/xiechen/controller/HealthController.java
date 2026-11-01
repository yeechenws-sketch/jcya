package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.service.HealthMonitoringService;
import com.chat.older.xiechen.vo.HealthCardVO;
import com.chat.older.xiechen.vo.DailyTrendVO;
import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.UserHealthConfigMapper;
import com.chat.older.xiechen.dao.EmergencyContactMapper;
import com.chat.older.xiechen.dao.UserMapper;
import com.chat.older.xiechen.dao.UserProfileMapper;
import com.chat.older.xiechen.dao.MessageMapper;
import com.chat.older.xiechen.dao.HealthConfigLogMapper;
import com.chat.older.xiechen.model.UserHealthConfig;
import com.chat.older.xiechen.model.EmergencyContact;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.model.UserProfile;
import com.chat.older.xiechen.model.Message;
import com.chat.older.xiechen.model.HealthConfigLog;
import com.chat.older.xiechen.service.NotificationService;
import com.chat.older.xiechen.handler.ChatHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@RestController
@RequestMapping("/api/health")
public class HealthController {

    @Autowired
    private HealthMonitoringService healthMonitoringService;

    @Autowired
    private UserHealthConfigMapper userHealthConfigMapper;

    @Autowired
    private EmergencyContactMapper emergencyContactMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ChatHandler chatHandler;

    @Autowired
    private HealthConfigLogMapper healthConfigLogMapper;

    @GetMapping("/cards")
    public Result<List<HealthCardVO>> getHealthCards(@RequestParam Long userId) {
        try {
            List<HealthCardVO> cards = healthMonitoringService.getHealthCards(userId);
            return Result.success(cards);
        } catch (Exception e) {
            return Result.error("获取健康卡片失败: " + e.getMessage());
        }
    }

    @GetMapping("/trend")
    public Result<DailyTrendVO> getDailyTrend(@RequestParam Long userId, @RequestParam(defaultValue = "7") int days) {
        try {
            DailyTrendVO trend = healthMonitoringService.getDailyTrend(userId, days);
            return Result.success(trend);
        } catch (Exception e) {
            return Result.error("获取健康趋势失败: " + e.getMessage());
        }
    }

    @GetMapping("/latest")
    public Result<?> getLatestVitalSign(@RequestParam Long userId, @RequestParam String type) {
        try {
            var vitalSign = healthMonitoringService.getLatestVitalSign(userId, type);
            return Result.success(vitalSign);
        } catch (Exception e) {
            return Result.error("获取最新体征数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/history")
    public Result<?> getVitalSignsHistory(@RequestParam Long userId, @RequestParam String type, @RequestParam(defaultValue = "10") int limit) {
        try {
            var history = healthMonitoringService.getVitalSignsHistory(userId, type, limit);
            return Result.success(history);
        } catch (Exception e) {
            return Result.error("获取体征历史数据失败: " + e.getMessage());
        }
    }


    @GetMapping("/config/{userId}")
    public Result<UserHealthConfig> getHealthConfig(@PathVariable Long userId) {
        try {
            UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
            if (config == null) {
                config = createDefaultConfig(userId);
            }
            return Result.success(config);
        } catch (Exception e) {
            return Result.error("获取健康配置失败: " + e.getMessage());
        }
    }


    @GetMapping("/config/current")
    public Result<UserHealthConfig> getCurrentUserHealthConfig(HttpSession session) {
        try {
            User currUser = (User) session.getAttribute("currUser");
            if (currUser == null) {
                return Result.error("用户未登录");
            }
            Long userId = currUser.getId();
            UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
            if (config == null) {
                config = createDefaultConfig(userId);
            }
            return Result.success(config);
        } catch (Exception e) {
            return Result.error("获取健康配置失败: " + e.getMessage());
        }
    }


    @PostMapping("/config/reset/current")
    public Result<UserHealthConfig> resetCurrentUserHealthConfig(HttpSession session) {
        try {
            User currUser = (User) session.getAttribute("currUser");
            if (currUser == null) {
                return Result.error("用户未登录");
            }
            Long userId = currUser.getId();

            UserHealthConfig config = createDefaultConfig(userId);
            config.setUpdateTime(new Date());

            int result = userHealthConfigMapper.upsert(config);

            if (result > 0) {
                String operatorName = currUser.getNickname() != null ? currUser.getNickname() : "用户";
                sendHealthConfigChangeNotification(userId, userId, operatorName, "重置为默认值");
                return Result.success(config);
            } else {
                return Result.error("重置健康配置失败");
            }
        } catch (Exception e) {
            return Result.error("重置健康配置失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户健康阈值配置
     * 长辈端和子女端都可修改
     * - 长辈修改：发送通知给子女/紧急联系人
     * - 子女修改：发送通知给长辈
     */
    @PostMapping("/config/update")
    public Result<?> updateHealthConfig(@RequestBody Map<String, Object> params, HttpSession session) {
        try {
            User currUser = (User) session.getAttribute("currUser");
            if (currUser == null) {
                return Result.error("用户未登录");
            }

            Long targetUserId;
            if (params.get("userId") != null && !params.get("userId").toString().isEmpty()) {
                targetUserId = Long.valueOf(params.get("userId").toString());
            } else {
                targetUserId = currUser.getId();
            }

            Long operatorId = currUser.getId();
            String operatorName = currUser.getNickname() != null ? currUser.getNickname() : "用户";

            User targetUser = userMapper.getById(targetUserId);
            String targetUserName = targetUser != null ? targetUser.getNickname() : "用户";

            UserHealthConfig config = userHealthConfigMapper.selectByUserId(targetUserId);
            boolean isNewConfig = (config == null);

            if (isNewConfig) {
                config = createDefaultConfig(targetUserId);
            }

            UserHealthConfig oldConfig = new UserHealthConfig();
            if (!isNewConfig) {
                oldConfig.setHrMin(config.getHrMin());
                oldConfig.setHrMax(config.getHrMax());
                oldConfig.setBoMin(config.getBoMin());
                oldConfig.setBpSysMax(config.getBpSysMax());
                oldConfig.setBpDiaMax(config.getBpDiaMax());
                oldConfig.setBgFastingMax(config.getBgFastingMax());
                oldConfig.setStepTarget(config.getStepTarget());
                oldConfig.setSleepTarget(config.getSleepTarget());
            }

            String changes = updateConfigFromParams(config, params);
            config.setUpdateTime(new Date());

            int result;
            if (isNewConfig) {
                result = userHealthConfigMapper.insert(config);
            } else {
                result = userHealthConfigMapper.update(config);
            }

            if (result > 0) {
                if (!isNewConfig && params.get("hrMin") != null && !params.get("hrMin").toString().equals(String.valueOf(oldConfig.getHrMin()))) {
                    saveConfigLog(targetUserId, targetUserName, operatorId, operatorName, "hrMin",
                            String.valueOf(oldConfig.getHrMin()), params.get("hrMin").toString(), "心率下限变更");
                }
                if (!isNewConfig && params.get("hrMax") != null && !params.get("hrMax").toString().equals(String.valueOf(oldConfig.getHrMax()))) {
                    saveConfigLog(targetUserId, targetUserName, operatorId, operatorName, "hrMax",
                            String.valueOf(oldConfig.getHrMax()), params.get("hrMax").toString(), "心率上限变更");
                }
                if (!isNewConfig && params.get("boMin") != null && !params.get("boMin").toString().equals(String.valueOf(oldConfig.getBoMin()))) {
                    saveConfigLog(targetUserId, targetUserName, operatorId, operatorName, "boMin",
                            String.valueOf(oldConfig.getBoMin()), params.get("boMin").toString(), "血氧下限变更");
                }
                if (!isNewConfig && params.get("bpSysMax") != null && !params.get("bpSysMax").toString().equals(String.valueOf(oldConfig.getBpSysMax()))) {
                    saveConfigLog(targetUserId, targetUserName, operatorId, operatorName, "bpSysMax",
                            String.valueOf(oldConfig.getBpSysMax()), params.get("bpSysMax").toString(), "收缩压上限变更");
                }
                if (!isNewConfig && params.get("bpDiaMax") != null && !params.get("bpDiaMax").toString().equals(String.valueOf(oldConfig.getBpDiaMax()))) {
                    saveConfigLog(targetUserId, targetUserName, operatorId, operatorName, "bpDiaMax",
                            String.valueOf(oldConfig.getBpDiaMax()), params.get("bpDiaMax").toString(), "舒张压上限变更");
                }
                if (!isNewConfig && params.get("bgFastingMax") != null && !params.get("bgFastingMax").toString().equals(String.valueOf(oldConfig.getBgFastingMax()))) {
                    saveConfigLog(targetUserId, targetUserName, operatorId, operatorName, "bgFastingMax",
                            String.valueOf(oldConfig.getBgFastingMax()), params.get("bgFastingMax").toString(), "空腹血糖上限变更");
                }
                if (!isNewConfig && params.get("stepTarget") != null && !params.get("stepTarget").toString().equals(String.valueOf(oldConfig.getStepTarget()))) {
                    saveConfigLog(targetUserId, targetUserName, operatorId, operatorName, "stepTarget",
                            String.valueOf(oldConfig.getStepTarget()), params.get("stepTarget").toString(), "步数目标变更");
                }
                if (!isNewConfig && params.get("sleepTarget") != null && !params.get("sleepTarget").toString().equals(String.valueOf(oldConfig.getSleepTarget()))) {
                    saveConfigLog(targetUserId, targetUserName, operatorId, operatorName, "sleepTarget",
                            String.valueOf(oldConfig.getSleepTarget()), params.get("sleepTarget").toString(), "睡眠目标变更");
                }

                sendHealthConfigChangeNotification(targetUserId, operatorId, operatorName, changes);
                return Result.success(config);
            } else {
                return Result.error("健康阈值配置更新失败");
            }
        } catch (Exception e) {
            return Result.error("更新健康配置失败: " + e.getMessage());
        }
    }


    @PostMapping("/config/reset/{userId}")
    public Result<UserHealthConfig> resetHealthConfig(@PathVariable Long userId, HttpSession session) {
        try {
            User currUser = (User) session.getAttribute("currUser");
            if (currUser == null) {
                return Result.error("用户未登录");
            }

            String operatorName = currUser.getNickname() != null ? currUser.getNickname() : "用户";

            UserHealthConfig config = createDefaultConfig(userId);
            config.setUpdateTime(new Date());

            int result = userHealthConfigMapper.upsert(config);

            if (result > 0) {
                sendHealthConfigChangeNotification(userId, currUser.getId(), operatorName, "重置为默认值");
                return Result.success(config);
            } else {
                return Result.error("重置健康配置失败");
            }
        } catch (Exception e) {
            return Result.error("重置健康配置失败: " + e.getMessage());
        }
    }


    private UserHealthConfig createDefaultConfig(Long userId) {
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
        return config;
    }


    private String updateConfigFromParams(UserHealthConfig config, Map<String, Object> params) {
        StringBuilder changes = new StringBuilder();

        if (params.get("hrMin") != null) {
            int newVal = Integer.parseInt(params.get("hrMin").toString());
            if (config.getHrMin() != newVal) {
                changes.append("心率下限: ").append(config.getHrMin()).append("→").append(newVal).append("; ");
            }
            config.setHrMin(newVal);
        }
        if (params.get("hrMax") != null) {
            int newVal = Integer.parseInt(params.get("hrMax").toString());
            if (config.getHrMax() != newVal) {
                changes.append("心率上限: ").append(config.getHrMax()).append("→").append(newVal).append("; ");
            }
            config.setHrMax(newVal);
        }
        if (params.get("boMin") != null) {
            int newVal = Integer.parseInt(params.get("boMin").toString());
            if (config.getBoMin() != newVal) {
                changes.append("血氧下限: ").append(config.getBoMin()).append("→").append(newVal).append("; ");
            }
            config.setBoMin(newVal);
        }
        if (params.get("bpSysMax") != null) {
            int newVal = Integer.parseInt(params.get("bpSysMax").toString());
            if (config.getBpSysMax() != newVal) {
                changes.append("收缩压上限: ").append(config.getBpSysMax()).append("→").append(newVal).append("; ");
            }
            config.setBpSysMax(newVal);
        }
        if (params.get("bpDiaMax") != null) {
            int newVal = Integer.parseInt(params.get("bpDiaMax").toString());
            if (config.getBpDiaMax() != newVal) {
                changes.append("舒张压上限: ").append(config.getBpDiaMax()).append("→").append(newVal).append("; ");
            }
            config.setBpDiaMax(newVal);
        }
        if (params.get("bgFastingMax") != null) {
            float newVal = Float.parseFloat(params.get("bgFastingMax").toString());
            if (config.getBgFastingMax() != newVal) {
                changes.append("空腹血糖上限: ").append(config.getBgFastingMax()).append("→").append(newVal).append("; ");
            }
            config.setBgFastingMax(newVal);
        }
        if (params.get("stepTarget") != null) {
            int newVal = Integer.parseInt(params.get("stepTarget").toString());
            if (config.getStepTarget() != newVal) {
                changes.append("步数目标: ").append(config.getStepTarget()).append("→").append(newVal).append("; ");
            }
            config.setStepTarget(newVal);
        }
        if (params.get("sleepTarget") != null) {
            float newVal = Float.parseFloat(params.get("sleepTarget").toString());
            if (config.getSleepTarget() != newVal) {
                changes.append("睡眠目标: ").append(config.getSleepTarget()).append("→").append(newVal).append("; ");
            }
            config.setSleepTarget(newVal);
        }

        return changes.length() > 0 ? changes.toString() : "无变更";
    }

    private void saveConfigLog(Long targetUserId, String targetUserName, Long operatorId, String operatorName,
                               String configField, String oldValue, String newValue, String changeDescription) {
        try {
            User operator = userMapper.getById(operatorId);
            String operatorType = operatorId.equals(targetUserId) ? "elder" : "family";
            if (operator != null && "admin".equals(operator.getRole())) {
                operatorType = "admin";
            }

            HealthConfigLog log = new HealthConfigLog();
            log.setUserId(targetUserId);
            log.setUserName(targetUserName);
            log.setOperatorId(operatorId);
            log.setOperatorName(operatorName);
            log.setOperatorType(operatorType);
            log.setConfigField(configField);
            log.setOldValue(oldValue);
            log.setNewValue(newValue);
            log.setChangeDescription(changeDescription);
            log.setCreateTime(new Date());

            healthConfigLogMapper.insert(log);
        } catch (Exception e) {
            System.err.println("[健康配置] 记录日志失败: " + e.getMessage());
        }
    }


    private void sendHealthConfigChangeNotification(Long targetUserId, Long operatorId, String operatorName, String changes) {
        try {
            User targetUser = userMapper.getById(targetUserId);
            if (targetUser == null) return;

            UserProfile targetProfile = userProfileMapper.selectById(targetUserId);

            boolean isOlderModify = operatorId.equals(targetUserId);

            if (isOlderModify) {
                String content = String.format("【健康阈值变更】%s 修改了健康监测阈值配置。\n变更内容：%s",
                        operatorName, changes);

                List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(targetUserId);
                for (EmergencyContact contact : contacts) {
                    if (contact.getContactUserId() != null) {
                        sendChatMessage(operatorId, contact.getContactUserId(), content, "health_config");
                    }
                    if (contact.getContactPhone() != null && !contact.getContactPhone().isEmpty()) {
                        notificationService.sendSms(contact.getContactPhone(), content);
                    }
                }
            } else {
                String content = String.format("【健康阈值变更】%s 为您修改了健康监测阈值配置。\n变更内容：%s\n如有疑问请及时沟通。",
                        operatorName, changes);

                sendChatMessage(operatorId, targetUserId, content, "health_config");

                if (targetProfile != null && targetProfile.getUserPhone() != null && !targetProfile.getUserPhone().isEmpty()) {
                    notificationService.sendSms(targetProfile.getUserPhone(), content);
                }
            }

            System.out.println("[健康配置] 已发送变更通知");
        } catch (Exception e) {
            System.err.println("[健康配置] 发送通知失败: " + e.getMessage());
        }
    }


    private void sendChatMessage(Long fromId, Long toId, String content, String msgType) {
        try {
            Message msg = new Message();
            msg.setFromId(fromId);
            msg.setToId(toId);
            msg.setContent(content);
            msg.setMsgType(msgType);
            msg.setIsRead(0);
            msg.setCreateTime(new Date());

            messageMapper.insertMessage(msg);

            Map<String, Object> wsMsg = new HashMap<>();
            wsMsg.put("fromId", fromId);
            wsMsg.put("toId", toId);
            wsMsg.put("content", content);
            wsMsg.put("msgType", msgType);
            wsMsg.put("createTime", System.currentTimeMillis());

            chatHandler.sendToUser(toId, wsMsg);

            System.out.println("[健康配置] 已发送聊天消息: fromId=" + fromId + ", toId=" + toId);
        } catch (Exception e) {
            System.err.println("[健康配置] 发送聊天消息失败: " + e.getMessage());
        }
    }


    @PostMapping("/heart-rate/save")
    public Result<?> saveHeartRate(@RequestBody Map<String, Object> params) {
        try {
            Long userId = Long.valueOf(params.get("userId").toString());
            Float heartRate = Float.valueOf(params.get("heartRate").toString());
            String deviceId = params.get("deviceId") != null ? params.get("deviceId").toString() : "BLE_HR_DEVICE";

            healthMonitoringService.processVitalSignsData(userId, "HR", heartRate, null, deviceId);

            return Result.success("心率数据保存成功");
        } catch (Exception e) {
            return Result.error("保存心率数据失败: " + e.getMessage());
        }
    }
}

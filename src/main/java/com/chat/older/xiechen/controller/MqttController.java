package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.handler.ChatHandler;
import com.chat.older.xiechen.service.MedicineBoxService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import com.chat.older.xiechen.model.User;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.jdbc.core.JdbcTemplate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mqtt")
public class MqttController {

    private static final Logger log = LoggerFactory.getLogger(MqttController.class);

    @Value("${mqtt.server.uri:tcp://localhost:1883}")
    private String serverUri;
    
    @Value("${mqtt.enabled:false}")
    private boolean mqttEnabled;
    
    @Value("${mqtt.username:}")
    private String username;
    
    @Value("${mqtt.password:}")
    private String password;
    
    @Autowired
    private MedicineBoxService medicineBoxService;
    
    @Autowired
    private ChatHandler chatHandler;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    private MqttClient mqttClient;
    private boolean mqttConnected = false;
    
    @PostConstruct
    public void init() {
        if (mqttEnabled) {
            tryConnectMqtt();
        }
    }
    
    @PreDestroy
    public void destroy() {
        if (mqttClient != null && mqttClient.isConnected()) {
            try {
                mqttClient.disconnect();
                mqttClient.close();
            } catch (Exception e) {
                log.warn("MQTT关闭失败", e);
            }
        }
    }
    
    private void tryConnectMqtt() {
        try {
            String clientId = "mqtt-pub-" + System.currentTimeMillis();
            mqttClient = new MqttClient(serverUri, clientId, new MemoryPersistence());
            MqttConnectOptions options = new MqttConnectOptions();
            options.setCleanSession(true);
            options.setConnectionTimeout(5);
            options.setKeepAliveInterval(60);
            options.setAutomaticReconnect(true);
            if (username != null && !username.isEmpty()) {
                options.setUserName(username);
            }
            if (password != null && !password.isEmpty()) {
                options.setPassword(password.toCharArray());
            }
            mqttClient.connect(options);
            mqttConnected = true;
            log.info("MQTT连接成功: {}", serverUri);
        } catch (Exception e) {
            mqttConnected = false;
            log.warn("MQTT连接失败: {}，将使用模拟模式", e.getMessage());
        }
    }

    @GetMapping("/status")
    public Result getMqttStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", mqttEnabled);
        status.put("connected", mqttConnected);
        status.put("serverUri", serverUri);
        return Result.success(status);
    }

    @PostMapping("/publish")
    public Result publishMessage(@RequestBody Map<String, Object> params, HttpSession session) {
        String deviceId = (String) params.get("deviceId");
        String event = (String) params.get("event");
        
        if (deviceId == null || event == null) {
            return Result.error("参数不完整");
        }
        
        String topic = "medicine/box/" + deviceId + "/event";
        String payload = "{\"event\":\"" + event + "\",\"timestamp\":" + System.currentTimeMillis() + "}";
        
        log.info("[MQTT] 发送事件: 设备={}, 事件={}", deviceId, event);
        
        if (!mqttEnabled || !mqttConnected) {
            log.info("[MQTT模拟模式] 发送消息到主题: {}", topic);
            return Result.success("模拟发送成功（MQTT未连接）");
        }
        
        try {
            publishMqtt(topic, payload);
            return Result.success("消息已发送");
        } catch (Exception e) {
            log.warn("[MQTT发送失败] {}", e.getMessage());
            mqttConnected = false;
            return Result.success("模拟发送成功（MQTT连接断开）");
        }
    }

    @PostMapping("/simulateTake")
    public Result simulateTakeMedicine(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.getOrDefault("deviceId", "BOX001");
        
        log.info("[模拟服药] 用户: {}, 设备: {}", user.getId(), deviceId);
        
        Result result = medicineBoxService.simulateTakeMedicine(user.getId(), deviceId);
        
        if (result.getCode() == 200) {
            String topic = "medicine/box/" + deviceId + "/takeout";
            String payload = "{\"event\":\"takeout\",\"timestamp\":" + System.currentTimeMillis() + "}";
            
            if (mqttEnabled && mqttConnected) {
                try {
                    publishMqtt(topic, payload);
                } catch (Exception e) {
                    log.warn("MQTT发布失败: {}", e.getMessage());
                }
            }
            
            log.info("[模拟服药成功] 用户ID: {}, 设备: {}", user.getId(), deviceId);
        }
        
        return result;
    }

    @PostMapping("/deviceTake/optimized")
    public Result takeMedicineByDeviceOptimized(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.getOrDefault("deviceId", "BOX001");
        Long medicineId = params.get("medicineId") != null ? 
            ((Number) params.get("medicineId")).longValue() : null;
        String medicineName = (String) params.get("medicineName");
        
        Double weightBefore = params.get("weightBefore") != null ? 
            ((Number) params.get("weightBefore")).doubleValue() : null;
        Double weightAfter = params.get("weightAfter") != null ? 
            ((Number) params.get("weightAfter")).doubleValue() : null;
        Double weightDiff = params.get("weightDiff") != null ? 
            ((Number) params.get("weightDiff")).doubleValue() : null;
        
        Boolean deviceConnected = params.get("deviceConnected") != null ? 
            (Boolean) params.get("deviceConnected") : null;
        Boolean boxLocked = params.get("boxLocked") != null ? 
            (Boolean) params.get("boxLocked") : null;
        Integer batteryLevel = params.get("batteryLevel") != null ? 
            ((Number) params.get("batteryLevel")).intValue() : null;
        
        Double calibrationFactor = params.get("calibrationFactor") != null ? 
            ((Number) params.get("calibrationFactor")).doubleValue() : null;
        
        @SuppressWarnings("unchecked")
        List<Double> weightMeasurements = (List<Double>) params.get("weightMeasurements");
        
        log.info("[真实设备服药优化] 用户: {}, 设备: {}, 药品ID: {}", user.getId(), deviceId, medicineId);
        
        Result result = medicineBoxService.takeMedicineByDeviceOptimized(
            user.getId(), deviceId, medicineId, medicineName,
            weightBefore, weightAfter, weightDiff,
            deviceConnected, boxLocked, batteryLevel,
            calibrationFactor, weightMeasurements
        );
        
        Map<String, Object> messageData = null;
        if (result.getCode() == 200 && result.getData() != null) {
            Map<String, Object> data = (Map<String, Object>) result.getData();
            String takeResult = (String) data.get("takeResult");
            String topic = "medicine/box/" + deviceId + "/takeout";
            String payload = String.format(
                "{\"event\":\"takeout\",\"takeResult\":\"%s\",\"timestamp\":%d}",
                takeResult != null ? takeResult : "normal",
                System.currentTimeMillis()
            );
            
            if (mqttEnabled && mqttConnected) {
                try {
                    publishMqtt(topic, payload);
                } catch (Exception e) {
                    log.warn("MQTT发布失败: {}", e.getMessage());
                }
            }
            
            messageData = data;
            log.info("[真实设备服药优化成功] 用户ID: {}, 设备: {}, 结果: {}", 
                user.getId(), deviceId, takeResult);
        } else if (result.getCode() != 200 && result.getData() != null) {
            messageData = (Map<String, Object>) result.getData();
        } else if (result.getCode() != 200) {
            messageData = new HashMap<>();
            messageData.put("message", result.getMsg());
        }
        
        if (messageData != null) {
            sendMedicationResultToElder(user.getId(), messageData);
        }
        
        return result;
    }

    @GetMapping("/box/status")
    public Result getBoxStatus(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        return medicineBoxService.getBoxStatus(user.getId());
    }
    
    @GetMapping("/box/status/{elderId}")
    public Result getElderBoxStatus(@PathVariable Long elderId, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        return medicineBoxService.getBoxStatus(elderId);
    }
    
    @GetMapping("/box/medication-logs/{elderId}")
    public Result getElderMedicationLogs(@PathVariable Long elderId, 
                                         @RequestParam(required = false) String date,
                                         HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        return medicineBoxService.getMedicationLogs(elderId, date);
    }

    @PostMapping("/box/register")
    public Result registerBox(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.get("deviceId");
        String boxName = (String) params.get("boxName");
        
        return medicineBoxService.registerBox(user.getId(), deviceId, boxName);
    }

    @PostMapping("/slot/take/{slotId}")
    public Result takeMedicineBySlot(@PathVariable Long slotId, @RequestBody(required = false) Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String timePeriod = params != null ? (String) params.get("timePeriod") : null;
        String takeType = params != null ? (String) params.get("takeType") : "normal";
        return medicineBoxService.simulateTakeMedicineBySlot(user.getId(), slotId, timePeriod, takeType);
    }

    @GetMapping("/realtime-status")
    public Result getRealtimeMedicationStatus(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        return Result.success(medicineBoxService.getRealtimeMedicationStatus(user.getId()));
    }

    @PostMapping("/slot/bind")
    public Result bindSlotToTime(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        Long slotId = ((Number) params.get("slotId")).longValue();
        String timeSlot = (String) params.get("timeSlot");
        String slotName = (String) params.get("slotName");
        java.math.BigDecimal dosageWeight = params.get("dosageWeight") != null ? 
            new java.math.BigDecimal(params.get("dosageWeight").toString()) : new java.math.BigDecimal("0.1");
        
        return medicineBoxService.bindSlotToTimeSlot(slotId, timeSlot, slotName, dosageWeight);
    }

    @PostMapping("/slot/bindMedicine")
    public Result bindMedicineToSlot(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        Long slotId = ((Number) params.get("slotId")).longValue();
        Long userMedicineId = ((Number) params.get("userMedicineId")).longValue();
        
        return medicineBoxService.bindMedicineToSlot(user.getId(), slotId, userMedicineId);
    }

    @PostMapping("/slot/unbind/{slotId}")
    public Result unbindSlot(@PathVariable Long slotId, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        return medicineBoxService.unbindSlot(slotId);
    }

    @PostMapping("/slot/autoBind")
    public Result autoBindSlots(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        return medicineBoxService.autoBindSlots(user.getId());
    }

    @PostMapping("/timeslot/set")
    public Result setTimeSlot(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String slotName = (String) params.get("slotName");
        String timePoint = (String) params.get("timePoint");
        Integer graceMinutes = params.get("graceMinutes") != null ? 
            ((Number) params.get("graceMinutes")).intValue() : 30;
        
        return medicineBoxService.setTimeSlot(user.getId(), slotName, timePoint, graceMinutes);
    }

    @PostMapping("/device/timeoutRemind")
    public Result sendTimeoutRemind(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.getOrDefault("deviceId", "BOX001");
        log.info("[API] 发送超时提醒: deviceId={}", deviceId);
        sendDeviceCommand(deviceId, "timeout_remind", 4, "red");
        return Result.success("超时提醒已发送");
    }

    @PostMapping("/device/lowStock")
    public Result sendLowStockAlert(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.getOrDefault("deviceId", "BOX001");
        log.info("[API] 发送低库存提醒: deviceId={}", deviceId);
        sendDeviceCommand(deviceId, "low_stock", 5, "yellow");
        return Result.success("低库存提醒已发送");
    }

    @PostMapping("/device/systemError")
    public Result sendSystemError(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.getOrDefault("deviceId", "BOX001");
        log.info("[API] 发送系统异常提醒: deviceId={}", deviceId);
        sendDeviceCommand(deviceId, "system_error", 6, "red");
        return Result.success("系统异常提醒已发送");
    }
    
    private synchronized void publishMqtt(String topic, String payload) throws Exception {
        if (mqttClient == null || !mqttClient.isConnected()) {
            tryConnectMqtt();
            if (!mqttConnected) {
                throw new Exception("MQTT未连接");
            }
        }
        MqttMessage message = new MqttMessage(payload.getBytes());
        message.setQos(1);
        mqttClient.publish(topic, message);
    }
    
    public void sendDeviceCommand(String deviceId, String command, Integer fileNum, String ledColor) {
        sendDeviceCommand(deviceId, command, fileNum, ledColor, null);
    }
    
    public void sendDeviceCommand(String deviceId, String command, Integer fileNum, String ledColor, String ledEffect) {
        try {
            String topic = "medicine/box/" + deviceId + "/command";
            
            Map<String, Object> payload = new HashMap<>();
            payload.put("command", command);
            if (fileNum != null) {
                payload.put("fileNum", fileNum);
            }
            if (ledColor != null) {
                payload.put("color", ledColor);
            }
            if (ledEffect != null) {
                payload.put("effect", ledEffect);
            }
            
            String payloadJson = objectMapper.writeValueAsString(payload);
            
            log.info("[MQTT设备控制] 设备={}, 命令={}, 文件={}, LED={}, 效果={}", 
                deviceId, command, fileNum, ledColor, ledEffect);
            
            if (!mqttEnabled || !mqttConnected) {
                log.info("[MQTT模拟模式] 发送控制命令到主题: {}", topic);
                return;
            }
            
            publishMqtt(topic, payloadJson);
            log.info("[MQTT设备控制] 命令发送成功");
        } catch (Exception e) {
            log.warn("[MQTT设备控制] 发送失败: {}", e.getMessage());
        }
    }
    
    @PostMapping("/emergency/call")
    public Result deviceEmergencyCall(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.getOrDefault("deviceId", "BOX001");
        
        log.info("[设备紧急呼叫] 用户: {}, 设备: {}", user.getId(), deviceId);
        
        try {
            List<Map<String, Object>> contacts = jdbcTemplate.queryForList(
                "SELECT id, contact_user_id, contact_name, contact_phone FROM emergency_contacts WHERE user_id = ? AND is_deleted = 0",
                user.getId()
            );
            
            if (contacts == null || contacts.isEmpty()) {
                return Result.error("未设置紧急联系人");
            }
            
            Map<String, Object> recordData = new HashMap<>();
            recordData.put("elderUserId", user.getId());
            recordData.put("elderUserName", user.getNickname());
            recordData.put("callType", "device");
            recordData.put("deviceId", deviceId);
            recordData.put("status", "calling");
            recordData.put("notificationStatus", "pending");
            
            jdbcTemplate.update(
                "INSERT INTO emergency_call_records (elder_user_id, elder_user_name, call_type, device_id, start_time, status, notification_status, create_time) " +
                "VALUES (?, ?, ?, ?, NOW(), ?, ?, NOW())",
                user.getId(), user.getNickname(), "device", deviceId, "calling", "pending"
            );
            
            List<Long> contactUserIds = new ArrayList<>();
            for (Map<String, Object> contact : contacts) {
                Object contactUserIdObj = contact.get("contact_user_id");
                if (contactUserIdObj != null) {
                    contactUserIds.add(((Number) contactUserIdObj).longValue());
                }
            }
            
            Map<String, Object> emergencyMessage = new HashMap<>();
            emergencyMessage.put("msgType", "EMERGENCY_CALL_NOTIFICATION");
            emergencyMessage.put("elderId", user.getId());
            emergencyMessage.put("elderName", user.getNickname());
            emergencyMessage.put("deviceId", deviceId);
            emergencyMessage.put("timestamp", System.currentTimeMillis());
            emergencyMessage.put("content", user.getNickname() + " 按下了紧急呼叫按钮！");
            
            for (Long contactUserId : contactUserIds) {
                try {
                    chatHandler.sendToUser(contactUserId, emergencyMessage);
                    log.info("[设备紧急呼叫] 已发送通知给联系人: {}", contactUserId);
                } catch (Exception e) {
                    log.warn("[设备紧急呼叫] 发送通知失败: {}", e.getMessage());
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("message", "紧急呼叫已发起");
            result.put("contacts", contacts);
            
            log.info("[设备紧急呼叫成功] 用户ID: {}, 设备: {}", user.getId(), deviceId);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("[设备紧急呼叫失败]", e);
            return Result.error("紧急呼叫失败: " + e.getMessage());
        }
    }
    
    private void sendMedicationResultToElder(Long userId, Map<String, Object> resultData) {
        try {
            log.info("[推送服药结果] userId={}, resultData={}", userId, resultData);
            
            Map<String, Object> message = new HashMap<>();
            message.put("msgType", "MEDICATION_RESULT");
            if (resultData != null) {
                message.putAll(resultData);
            }
            message.put("timestamp", System.currentTimeMillis());
            
            log.info("[推送服药最终消息] userId={}, message={}", userId, message);
            
            chatHandler.sendToUser(userId, message);
            
            log.info("[推送服药结果成功] userId={}", userId);
        } catch (Exception e) {
            log.error("[推送服药结果失败]", e);
        }
    }
}

package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.service.MedicineBoxDeviceService;
import com.chat.older.xiechen.service.MedicineBoxService;
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
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/medicine-box")
public class MedicineBoxDeviceController {

    private static final Logger log = LoggerFactory.getLogger(MedicineBoxDeviceController.class);

    @Value("${mqtt.server.uri:tcp://localhost:1883}")
    private String serverUri;
    
    @Value("${mqtt.enabled:false}")
    private boolean mqttEnabled;
    
    @Value("${mqtt.username:}")
    private String username;
    
    @Value("${mqtt.password:}")
    private String password;
    
    @Autowired
    private MedicineBoxDeviceService deviceService;
    
    @Autowired
    private MedicineBoxService medicineBoxService;
    
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
            String clientId = "medicine-server-" + System.currentTimeMillis();
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
            
            mqttClient.setCallback(new org.eclipse.paho.client.mqttv3.MqttCallback() {
                @Override
                public void connectionLost(Throwable cause) {
                    mqttConnected = false;
                    log.warn("MQTT连接断开: {}", cause.getMessage());
                }
                
                @Override
                public void messageArrived(String topic, MqttMessage message) {
                    try {
                        String payload = new String(message.getPayload());
                        log.info("[MQTT消息] topic={}, payload={}", topic, payload);
                        
                        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                        Map<String, Object> msg = mapper.readValue(payload, Map.class);
                        msg.put("deviceId", extractDeviceIdFromTopic(topic));
                        
                        deviceService.handleDeviceMessage(msg);
                    } catch (Exception e) {
                        log.error("处理MQTT消息失败", e);
                    }
                }
                
                @Override
                public void deliveryComplete(org.eclipse.paho.client.mqttv3.IMqttDeliveryToken token) {
                }
            });
            
            mqttClient.connect(options);
            mqttClient.subscribe("medicine/box/+/data");
            mqttConnected = true;
            log.info("MQTT连接成功: {}", serverUri);
        } catch (Exception e) {
            mqttConnected = false;
            log.warn("MQTT连接失败: {}，将使用HTTP模式", e.getMessage());
        }
    }
    
    private String extractDeviceIdFromTopic(String topic) {
        String[] parts = topic.split("/");
        if (parts.length >= 3) {
            return parts[2];
        }
        return "UNKNOWN";
    }

    @PostMapping("/device/message")
    public Result handleDeviceMessage(@RequestBody Map<String, Object> message) {
        return deviceService.handleDeviceMessage(message);
    }

    @PostMapping("/weight")
    public Result receiveWeight(@RequestBody Map<String, Object> params) {
        String deviceId = (String) params.get("deviceId");
        Object weightObj = params.get("weight");
        Integer slotId = params.get("slotId") != null ? 
            Integer.valueOf(params.get("slotId").toString()) : 1;
        
        if (deviceId == null || deviceId.isEmpty()) {
            return Result.error("设备ID不能为空");
        }
        if (weightObj == null) {
            return Result.error("重量数据不能为空");
        }
        
        Map<String, Object> message = new HashMap<>();
        message.put("deviceId", deviceId);
        message.put("slotId", slotId);
        message.put("eventType", "weight_change");
        message.put("weightAfter", Double.parseDouble(weightObj.toString()));
        message.put("timestamp", System.currentTimeMillis());
        
        return deviceService.handleDeviceMessage(message);
    }

    @PostMapping("/event")
    public Result receiveEvent(@RequestBody Map<String, Object> params) {
        String deviceId = (String) params.get("deviceId");
        String eventType = (String) params.get("eventType");
        Integer slotId = params.get("slotId") != null ? 
            Integer.valueOf(params.get("slotId").toString()) : 1;
        
        if (deviceId == null || deviceId.isEmpty()) {
            return Result.error("设备ID不能为空");
        }
        if (eventType == null || eventType.isEmpty()) {
            return Result.error("事件类型不能为空");
        }
        
        Map<String, Object> message = new HashMap<>(params);
        message.put("deviceId", deviceId);
        message.put("eventType", eventType);
        message.put("slotId", slotId);
        
        return deviceService.handleDeviceMessage(message);
    }

    @GetMapping("/devices/{userId}")
    public Result getUserDevices(@PathVariable Long userId) {
        return medicineBoxService.getBoxStatus(userId);
    }

    @GetMapping("/status/{userId}")
    public Result getBoxStatus(@PathVariable Long userId) {
        return medicineBoxService.getBoxStatus(userId);
    }

    @GetMapping("/device/status/{deviceId}")
    public Result getDeviceStatus(@PathVariable String deviceId) {
        return deviceService.getDeviceStatus(deviceId);
    }

    @PostMapping("/register")
    public Result registerDevice(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        Long userId;
        
        if (user != null) {
            userId = user.getId();
        } else if (params.get("userId") != null) {
            userId = Long.valueOf(params.get("userId").toString());
        } else {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.get("deviceId");
        String boxName = (String) params.get("boxName");
        
        if (deviceId == null || deviceId.isEmpty()) {
            return Result.error("设备ID不能为空");
        }
        
        return deviceService.registerDevice(userId, deviceId, boxName);
    }

    @PostMapping("/initialize")
    public Result initializeDevice(@RequestBody Map<String, Object> params) {
        String deviceId = (String) params.get("deviceId");
        Long userId = params.get("userId") != null ? 
            Long.valueOf(params.get("userId").toString()) : null;
        
        if (deviceId == null || deviceId.isEmpty()) {
            return Result.error("设备ID不能为空");
        }
        if (userId == null) {
            return Result.error("用户ID不能为空");
        }
        
        String step = (String) params.get("step");
        if (step != null) {
            return deviceService.startInitialization(deviceId, userId, step);
        }
        
        java.math.BigDecimal fullBottleWeight = params.get("fullBottleWeight") != null ? 
            new java.math.BigDecimal(params.get("fullBottleWeight").toString()) : null;
        java.math.BigDecimal singleDoseWeight = params.get("singleDoseWeight") != null ? 
            new java.math.BigDecimal(params.get("singleDoseWeight").toString()) : null;
        Integer pillsPerBottle = params.get("pillsPerBottle") != null ? 
            Integer.valueOf(params.get("pillsPerBottle").toString()) : null;
        
        if (fullBottleWeight == null || singleDoseWeight == null) {
            return Result.error("请提供整瓶重量和单次剂量");
        }
        
        return deviceService.initializeDevice(deviceId, userId, fullBottleWeight, singleDoseWeight, pillsPerBottle);
    }

    @PostMapping("/bind-slot")
    public Result bindSlot(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        Long slotId = Long.valueOf(params.get("slotId").toString());
        Long userMedicineId = Long.valueOf(params.get("userMedicineId").toString());
        
        return medicineBoxService.bindMedicineToSlot(user.getId(), slotId, userMedicineId);
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

    @GetMapping("/mqtt/status")
    public Result getMqttStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("enabled", mqttEnabled);
        status.put("connected", mqttConnected);
        status.put("serverUri", serverUri);
        return Result.success(status);
    }

    @GetMapping("/realtime-status")
    public Result getRealtimeMedicationStatus(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        return Result.success(medicineBoxService.getRealtimeMedicationStatus(user.getId()));
    }

    @GetMapping("/medication-logs/{userId}")
    public Result getMedicationLogs(@PathVariable Long userId, 
                                    @RequestParam(required = false) String date,
                                    HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        return medicineBoxService.getMedicationLogs(userId, date);
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
    
    @PostMapping("/device/take")
    public Result takeMedicineByDevice(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.get("deviceId");
        Long medicineId = params.get("medicineId") != null ? Long.valueOf(params.get("medicineId").toString()) : null;
        String medicineName = (String) params.get("medicineName");
        Integer quantity = params.get("quantity") != null ? Integer.valueOf(params.get("quantity").toString()) : 1;
        String takeResult = (String) params.getOrDefault("takeResult", "normal");
        String unit = (String) params.getOrDefault("unit", "粒");
        
        Double weightBefore = params.get("weightBefore") != null ? Double.valueOf(params.get("weightBefore").toString()) : null;
        Double weightAfter = params.get("weightAfter") != null ? Double.valueOf(params.get("weightAfter").toString()) : null;
        Double weightDiff = params.get("weightDiff") != null ? Double.valueOf(params.get("weightDiff").toString()) : null;
        
        return medicineBoxService.takeMedicineByDevice(user.getId(), deviceId, medicineId, medicineName, quantity, takeResult, unit, weightBefore, weightAfter, weightDiff);
    }
    
    @GetMapping("/device/{deviceId}/binding")
    public Result getDeviceBinding(@PathVariable String deviceId, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        return medicineBoxService.getDeviceBinding(user.getId(), deviceId);
    }
    
    @PostMapping("/device/bind")
    public Result bindDevice(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.get("deviceId");
        Long medicineId = params.get("medicineId") != null ? Long.valueOf(params.get("medicineId").toString()) : null;
        String medicineName = (String) params.get("medicineName");
        Integer initStep = params.get("initStep") != null ? Integer.valueOf(params.get("initStep").toString()) : 1;
        Double singleDoseWeight = params.get("singleDoseWeight") != null ? Double.valueOf(params.get("singleDoseWeight").toString()) : null;
        
        return medicineBoxService.bindDevice(user.getId(), deviceId, medicineId, medicineName, initStep, singleDoseWeight);
    }
    
    @PostMapping("/device/unbind")
    public Result unbindDevice(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.get("deviceId");
        return medicineBoxService.unbindDevice(user.getId(), deviceId);
    }
    
    @PostMapping("/notify/family")
    public Result notifyFamily(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String message = (String) params.get("message");
        String type = (String) params.getOrDefault("type", "medication_alert");
        return medicineBoxService.notifyFamily(user.getId(), message, type);
    }
    
    @PostMapping("/restock/confirm")
    public Result confirmRestock(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        Long slotId = params.get("slotId") != null ? Long.valueOf(params.get("slotId").toString()) : null;
        Long userMedicineId = params.get("userMedicineId") != null ? Long.valueOf(params.get("userMedicineId").toString()) : null;
        Integer restockQuantity = params.get("quantity") != null ? Integer.valueOf(params.get("quantity").toString()) : 1;
        
        if (slotId == null) {
            return Result.error("药仓ID不能为空");
        }
        
        return medicineBoxService.confirmRestock(user.getId(), slotId, userMedicineId, restockQuantity);
    }
    
    @PostMapping("/session/reset")
    public Result resetMedicationSession(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        Long slotId = params.get("slotId") != null ? Long.valueOf(params.get("slotId").toString()) : null;
        
        if (slotId == null) {
            return Result.error("药仓ID不能为空");
        }
        
        return medicineBoxService.resetMedicationSession(user.getId(), slotId);
    }
    
    @GetMapping("/medication-window/{userMedicineId}")
    public Result getMedicationWindow(@PathVariable Integer userMedicineId, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        return deviceService.getMedicationWindowInfoForApi(userMedicineId);
    }
    
    @PostMapping("/abnormal-take")
    public Result handleAbnormalTake(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.get("deviceId");
        Long medicineId = params.get("medicineId") != null ? Long.valueOf(params.get("medicineId").toString()) : null;
        String medicineName = (String) params.get("medicineName");
        Integer quantity = params.get("quantity") != null ? Integer.valueOf(params.get("quantity").toString()) : 1;
        String takeResult = (String) params.getOrDefault("takeResult", "blocked_not_returned");
        String unit = (String) params.getOrDefault("unit", "粒");
        
        Double weightBefore = params.get("weightBefore") != null ? Double.valueOf(params.get("weightBefore").toString()) : null;
        Double weightAfter = params.get("weightAfter") != null ? Double.valueOf(params.get("weightAfter").toString()) : null;
        Double weightDiff = params.get("weightDiff") != null ? Double.valueOf(params.get("weightDiff").toString()) : null;
        
        return medicineBoxService.takeMedicineByDevice(user.getId(), deviceId, medicineId, medicineName, quantity, takeResult, unit, weightBefore, weightAfter, weightDiff);
    }

    @PostMapping("/calculate-remaining-pills")
    public Result calculateRemainingPills(@RequestBody Map<String, Object> params, HttpSession session) {
        String deviceId = (String) params.get("deviceId");
        Integer slotId = params.get("slotId") != null ? Integer.valueOf(params.get("slotId").toString()) : 1;
        Object weightObj = params.get("currentWeight");
        
        if (deviceId == null || deviceId.isEmpty()) {
            return Result.error("设备ID不能为空");
        }
        if (weightObj == null) {
            return Result.error("当前重量不能为空");
        }
        
        java.math.BigDecimal currentWeight = new java.math.BigDecimal(weightObj.toString());
        return deviceService.calculateRemainingPillsByWeight(deviceId, slotId, currentWeight);
    }

    @PostMapping("/start-medication-confirmation")
    public Result startMedicationConfirmation(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return Result.error("请先登录");
        }
        
        String deviceId = (String) params.get("deviceId");
        Long slotId = params.get("slotId") != null ? Long.valueOf(params.get("slotId").toString()) : null;
        Integer userMedicineId = params.get("userMedicineId") != null ? Integer.valueOf(params.get("userMedicineId").toString()) : null;
        Object actualTakenWeightObj = params.get("actualTakenWeight");
        Object expectedDosageWeightObj = params.get("expectedDosageWeight");
        Integer expectedDosagePills = params.get("expectedDosagePills") != null ? Integer.valueOf(params.get("expectedDosagePills").toString()) : 1;
        
        if (deviceId == null || deviceId.isEmpty()) {
            return Result.error("设备ID不能为空");
        }
        if (slotId == null) {
            return Result.error("药仓ID不能为空");
        }
        
        java.math.BigDecimal actualTakenWeight = actualTakenWeightObj != null ? new java.math.BigDecimal(actualTakenWeightObj.toString()) : java.math.BigDecimal.ZERO;
        java.math.BigDecimal expectedDosageWeight = expectedDosageWeightObj != null ? new java.math.BigDecimal(expectedDosageWeightObj.toString()) : java.math.BigDecimal.ONE;
        
        return deviceService.startMedicationConfirmation(user.getId(), deviceId, slotId, userMedicineId, actualTakenWeight, expectedDosageWeight, expectedDosagePills);
    }

    @PostMapping("/confirm-pill-reduction")
    public Result confirmPillReduction(@RequestBody Map<String, Object> params) {
        String deviceId = (String) params.get("deviceId");
        Long slotId = params.get("slotId") != null ? Long.valueOf(params.get("slotId").toString()) : null;
        
        if (deviceId == null || deviceId.isEmpty()) {
            return Result.error("设备ID不能为空");
        }
        if (slotId == null) {
            return Result.error("药仓ID不能为空");
        }
        
        return deviceService.confirmPillReduction(deviceId, slotId);
    }

    @PostMapping("/click-take-confirmation")
    public Result clickTakeConfirmation(@RequestBody Map<String, Object> params) {
        String deviceId = (String) params.get("deviceId");
        Long slotId = params.get("slotId") != null ? Long.valueOf(params.get("slotId").toString()) : null;
        
        if (deviceId == null || deviceId.isEmpty()) {
            return Result.error("设备ID不能为空");
        }
        if (slotId == null) {
            return Result.error("药仓ID不能为空");
        }
        
        return deviceService.clickTakeConfirmationButton(deviceId, slotId);
    }

    @PostMapping("/verify-dosage")
    public Result verifyMedicationDosage(@RequestBody Map<String, Object> params) {
        Object weightBeforeObj = params.get("weightBefore");
        Object weightAfterObj = params.get("weightAfter");
        Object expectedDosageWeightObj = params.get("expectedDosageWeight");
        Object toleranceMinObj = params.get("toleranceMin");
        Object toleranceMaxObj = params.get("toleranceMax");
        
        if (weightBeforeObj == null || weightAfterObj == null || expectedDosageWeightObj == null || toleranceMinObj == null || toleranceMaxObj == null) {
            return Result.error("参数不完整");
        }
        
        java.math.BigDecimal weightBefore = new java.math.BigDecimal(weightBeforeObj.toString());
        java.math.BigDecimal weightAfter = new java.math.BigDecimal(weightAfterObj.toString());
        java.math.BigDecimal expectedDosageWeight = new java.math.BigDecimal(expectedDosageWeightObj.toString());
        java.math.BigDecimal toleranceMin = new java.math.BigDecimal(toleranceMinObj.toString());
        java.math.BigDecimal toleranceMax = new java.math.BigDecimal(toleranceMaxObj.toString());
        
        return deviceService.verifyMedicationDosageByWeight(weightBefore, weightAfter, expectedDosageWeight, toleranceMin, toleranceMax);
    }

    @PostMapping("/verify-dosage-with-refill")
    public Result verifyDosageWithRefill(@RequestBody Map<String, Object> params) {
        Object initialWeightBeforeObj = params.get("initialWeightBefore");
        Object initialWeightAfterObj = params.get("initialWeightAfter");
        Object refillWeightBeforeObj = params.get("refillWeightBefore");
        Object refillWeightAfterObj = params.get("refillWeightAfter");
        Object expectedDosageWeightObj = params.get("expectedDosageWeight");
        Object toleranceMinObj = params.get("toleranceMin");
        Object toleranceMaxObj = params.get("toleranceMax");
        
        if (initialWeightBeforeObj == null || initialWeightAfterObj == null || refillWeightBeforeObj == null || 
            refillWeightAfterObj == null || expectedDosageWeightObj == null || toleranceMinObj == null || toleranceMaxObj == null) {
            return Result.error("参数不完整");
        }
        
        java.math.BigDecimal initialWeightBefore = new java.math.BigDecimal(initialWeightBeforeObj.toString());
        java.math.BigDecimal initialWeightAfter = new java.math.BigDecimal(initialWeightAfterObj.toString());
        java.math.BigDecimal refillWeightBefore = new java.math.BigDecimal(refillWeightBeforeObj.toString());
        java.math.BigDecimal refillWeightAfter = new java.math.BigDecimal(refillWeightAfterObj.toString());
        java.math.BigDecimal expectedDosageWeight = new java.math.BigDecimal(expectedDosageWeightObj.toString());
        java.math.BigDecimal toleranceMin = new java.math.BigDecimal(toleranceMinObj.toString());
        java.math.BigDecimal toleranceMax = new java.math.BigDecimal(toleranceMaxObj.toString());
        
        return deviceService.verifyDosageWithRefill(initialWeightBefore, initialWeightAfter, refillWeightBefore, refillWeightAfter, expectedDosageWeight, toleranceMin, toleranceMax);
    }
}

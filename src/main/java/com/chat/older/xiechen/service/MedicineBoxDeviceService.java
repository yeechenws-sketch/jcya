package com.chat.older.xiechen.service;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.controller.MqttController;
import com.chat.older.xiechen.handler.ChatHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONArray;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class MedicineBoxDeviceService {

    private static final Logger log = LoggerFactory.getLogger(MedicineBoxDeviceService.class);

    private static final BigDecimal EMPTY_THRESHOLD = new BigDecimal("5.0");
    private static final int EMERGENCY_TIMEOUT_SECONDS = 30;
    private static final int MEDICATION_CONFIRMATION_TIMEOUT_SECONDS = 60;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MqttController mqttController;

    @Autowired
    private ChatHandler chatHandler;
    
    @Autowired
    private MedicineBoxService medicineBoxService;

    private final Map<String, DeviceState> deviceStates = new ConcurrentHashMap<>();
    private final Map<String, EmergencyTimer> emergencyTimers = new ConcurrentHashMap<>();
    private final Map<String, MedicationConfirmationTimer> medicationConfirmationTimers = new ConcurrentHashMap<>();

    public void sendTimeoutRemind(String deviceId) {
        log.info("[发送超时提醒] deviceId={}", deviceId);
        mqttController.sendDeviceCommand(deviceId, "timeout_remind", 4, "red");
    }

    public void sendLowStockAlert(String deviceId) {
        log.info("[发送低库存提醒] deviceId={}", deviceId);
        mqttController.sendDeviceCommand(deviceId, "low_stock", 5, "yellow");
    }

    public void sendSystemError(String deviceId) {
        log.info("[发送系统异常提醒] deviceId={}", deviceId);
        mqttController.sendDeviceCommand(deviceId, "system_error", 6, "red");
    }

    public void sendMedicineReminder(String deviceId) {
        log.info("[发送吃药提醒] deviceId={}", deviceId);
        mqttController.sendDeviceCommand(deviceId, "remind_medicine", 2, "red", "blink");
    }

    public Result handleDeviceMessage(Map<String, Object> message) {
        try {
            String deviceId = (String) message.get("deviceId");
            String eventType = (String) message.get("eventType");

            if (deviceId == null || eventType == null) {
                return Result.error("参数不完整");
            }

            log.info("[设备消息] deviceId={}, eventType={}", deviceId, eventType);

            switch (eventType) {
                case "weight_change":
                    return handleWeightChange(message);
                case "bottle_removed":
                    return handleBottleRemoved(message);
                case "bottle_returned":
                    return handleBottleReturned(message);
                case "heartbeat":
                    return handleHeartbeat(message);
                case "takeout":
                    return handleTakeoutEvent(message);
                case "putback":
                    return handlePutbackEvent(message);
                case "low_stock":
                    return handleLowStockEvent(message);
                default:
                    log.warn("[未知事件类型] {}", eventType);
                    return Result.error("未知事件类型: " + eventType);
            }
        } catch (Exception e) {
            log.error("[设备消息处理失败]", e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }

    @Transactional
    public Result handleWeightChange(Map<String, Object> message) {
        String deviceId = (String) message.get("deviceId");
        Integer slotId = message.get("slotId") != null ? 
            ((Number) message.get("slotId")).intValue() : 1;
        BigDecimal weightBefore = message.get("weightBefore") != null ? 
            new BigDecimal(message.get("weightBefore").toString()) : BigDecimal.ZERO;
        BigDecimal weightAfter = new BigDecimal(message.get("weightAfter").toString());
        BigDecimal weightChange = weightBefore.subtract(weightAfter);

        Long userId = getUserIdByDeviceId(deviceId);
        if (userId == null) {
            return Result.error("设备未绑定用户");
        }

        Long boxId = getBoxIdByDeviceId(deviceId);
        if (boxId == null) {
            return Result.error("药盒不存在");
        }

        DeviceState state = deviceStates.computeIfAbsent(deviceId, k -> new DeviceState());
        state.lastWeight = weightAfter;
        state.lastUpdateTime = LocalDateTime.now();

        if (state.initStep != null) {
            Result result = handleInitWeightChange(deviceId, userId, boxId, weightBefore, weightAfter, weightChange);
            if (state.initStep == null) {
                calculateRemainingPillsByWeight(deviceId, slotId, weightAfter, true);
            }
            return result;
        }

        if (weightAfter.compareTo(EMPTY_THRESHOLD) < 0) {
            log.info("[药瓶移除] deviceId={}, weight={}", deviceId, weightAfter);
            return handleBottleRemoved(message);
        }

        BigDecimal MIN_WEIGHT_CHANGE_THRESHOLD = new BigDecimal("0.6");
        
        if (weightChange.compareTo(BigDecimal.ZERO) > 0) {
            if (weightChange.compareTo(MIN_WEIGHT_CHANGE_THRESHOLD) <= 0) {
                log.info("[重量变化忽略] deviceId={}, weightChange={}g < 阈值={}g，忽略微小变化", 
                    deviceId, weightChange, MIN_WEIGHT_CHANGE_THRESHOLD);
                calculateRemainingPillsByWeight(deviceId, slotId, weightAfter, true);
                return Result.success("重量变化小于阈值，已忽略");
            }
            Result result = handleTakeoutEvent(deviceId, userId, boxId, slotId, weightBefore, weightAfter, weightChange);
            calculateRemainingPillsByWeight(deviceId, slotId, weightAfter, true);
            return result;
        } else if (weightChange.compareTo(BigDecimal.ZERO) < 0) {
            if (weightChange.abs().compareTo(MIN_WEIGHT_CHANGE_THRESHOLD) <= 0) {
                log.info("[重量变化忽略] deviceId={}, weightChange={}g < 阈值={}g，忽略微小变化", 
                    deviceId, weightChange.abs(), MIN_WEIGHT_CHANGE_THRESHOLD);
                calculateRemainingPillsByWeight(deviceId, slotId, weightAfter, true);
                return Result.success("重量变化小于阈值，已忽略");
            }
            Result result = handleRefillEvent(deviceId, userId, boxId, slotId, weightBefore, weightAfter, weightChange.abs());
            calculateRemainingPillsByWeight(deviceId, slotId, weightAfter, true);
            return result;
        }

        calculateRemainingPillsByWeight(deviceId, slotId, weightAfter, true);

        return Result.success("重量更新成功");
    }

    @Transactional
    public Result handleInitWeightChange(String deviceId, Long userId, Long boxId, 
                                         BigDecimal weightBefore, BigDecimal weightAfter, BigDecimal weightChange) {
        DeviceState state = deviceStates.get(deviceId);
        if (state == null) {
            return Result.error("设备状态不存在");
        }

        if ("1".equals(state.initStep)) {
            jdbcTemplate.update(
                "UPDATE medicine_box SET init_first_weight = ?, update_time = NOW() WHERE device_id = ?",
                weightAfter, deviceId
            );
            
            if (weightAfter.compareTo(EMPTY_THRESHOLD) > 0) {
                state.initStep = "2";
                state.fullBottleWeight = weightAfter;
                
                jdbcTemplate.update(
                    "UPDATE medicine_box SET init_step = ?, update_time = NOW() WHERE device_id = ?",
                    2, deviceId
                );

                log.info("[初始化步骤1完成] deviceId={}, 完整药品重量={}g", deviceId, weightAfter);
                
                Map<String, Object> result = new HashMap<>();
                result.put("step", 1);
                result.put("stepComplete", true);
                result.put("fullBottleWeight", weightAfter);
                result.put("message", "完整药品称量完成！请取出一次用药的标准剂量后，再次放置在药盒上。");
                return Result.success(result);
            }
        } else if ("2".equals(state.initStep)) {
            jdbcTemplate.update(
                "UPDATE medicine_box SET second_weighing_weight = ?, update_time = NOW() WHERE device_id = ?",
                weightAfter, deviceId
            );
            
            if (state.fullBottleWeight != null && weightAfter.compareTo(EMPTY_THRESHOLD) > 0) {
                BigDecimal singleDose = state.fullBottleWeight.subtract(weightAfter);
                
                if (singleDose.compareTo(BigDecimal.ZERO) <= 0) {
                    return Result.error("请确保您取出了药品！当前重量没有变化或增加了。");
                }
                
                BigDecimal tolerance = singleDose.multiply(new BigDecimal("0.15"));
                BigDecimal toleranceMin = singleDose.subtract(tolerance);
                BigDecimal toleranceMax = singleDose.add(tolerance);
                
                state.initStep = null;
                state.initialized = true;
                state.singleDoseWeight = singleDose;
                state.secondWeighingWeight = weightAfter;
                
                jdbcTemplate.update(
                    "UPDATE medicine_box SET single_dose_weight = ?, full_bottle_weight = ?, " +
                    "tolerance_min = ?, tolerance_max = ?, second_weighing_weight = ?, " +
                    "initialized = 1, init_step = NULL, update_time = NOW() WHERE device_id = ?",
                    singleDose, state.fullBottleWeight, toleranceMin, toleranceMax, weightAfter, deviceId
                );

                recordBoxEvent(deviceId, userId, "init_complete", null, 
                    state.fullBottleWeight, weightAfter, singleDose);

                log.info("[初始化步骤2完成] deviceId={}, 完整重量={}g, 剩余重量={}g, 单次剂量={}g, 容忍度范围={}g~{}g", 
                    deviceId, state.fullBottleWeight, weightAfter, singleDose, toleranceMin, toleranceMax);
                
                Map<String, Object> result = new HashMap<>();
                result.put("step", 2);
                result.put("stepComplete", true);
                result.put("initComplete", true);
                result.put("fullBottleWeight", state.fullBottleWeight);
                result.put("remainingWeight", weightAfter);
                result.put("singleDoseWeight", singleDose);
                result.put("toleranceMin", toleranceMin);
                result.put("toleranceMax", toleranceMax);
                result.put("message", "药品初始化完成！系统已计算出单次用药剂量为 " + singleDose + "g，容忍度范围为 " + toleranceMin + "g~" + toleranceMax + "g。");
                return Result.success(result);
            }
        }

        return Result.success("等待继续操作...");
    }

    @Transactional
    public Result handleTakeoutEvent(String deviceId, Long userId, Long boxId, Integer slotId,
                                     BigDecimal weightBefore, BigDecimal weightAfter, BigDecimal weightChange) {
        try {
            Map<String, Object> slot = getSlotByBoxAndNumber(boxId, slotId);
            if (slot == null) {
                return Result.error("药仓不存在");
            }
            
            Integer userMedicineId = slot.get("user_medicine_id") != null ? 
                ((Number) slot.get("user_medicine_id")).intValue() : null;
            
            MedicationWindowInfo windowInfo = null;
            boolean inMedicationWindow = false;
            
            if (userMedicineId != null) {
                windowInfo = getMedicationWindowInfo(userMedicineId);
                LocalTime nowTime = LocalTime.now();
                
                if (windowInfo != null) {
                    inMedicationWindow = !nowTime.isBefore(windowInfo.earlyAllowTime) && !nowTime.isAfter(windowInfo.makeupDeadTime);
                    log.info("[时间窗口-取药事件-完整] userMedicineId={}, 当前时间={}, 提前允许={}, 补服截止={}, inMedicationWindow={}", 
                        userMedicineId, nowTime, windowInfo.earlyAllowTime, windowInfo.makeupDeadTime, inMedicationWindow);
                    
                    LocalDateTime nowDateTime = LocalDateTime.now();
                    LocalDateTime scheduledTime = LocalDateTime.of(nowDateTime.toLocalDate(), windowInfo.plannedTime);
                    
                    Map<String, Object> takenLog = checkTakenTodayByUserMedicineAndTime(userMedicineId, scheduledTime);
                    if (takenLog != null) {
                        log.warn("[重复服药警告] 用户已服用过该药品, userId={}, medicineId={}, deviceId={}", 
                            userId, userMedicineId, deviceId);
                        
                        mqttController.sendDeviceCommand(deviceId, "wrong_time", 8, "red", "alternate");
                        
                        Map<String, Object> warningData = new HashMap<>();
                        warningData.put("duplicate", true);
                        warningData.put("message", "您已服用过本次药品，请把药品放回药盒！");
                        
                        String takenTime = takenLog.get("taken_time") != null ? 
                            takenLog.get("taken_time").toString().substring(11, 16) : "";
                        warningData.put("detailMessage", "您已在 " + takenTime + " 服用过该药品，请不要重复服用，请将药品放回药盒。");
                        
                        return Result.error(400, "您已服用过本次药品，请把药品放回药盒！", warningData);
                    }
                } else {
                    inMedicationWindow = true;
                    log.info("[时间窗口-取药事件-兜底] userMedicineId={}, 无法获取窗口信息，默认允许", userMedicineId);
                }
            }

            Long slotIdLong = ((Number) slot.get("id")).longValue();
            String medicineName = (String) slot.get("slot_name");
            BigDecimal dosageWeight = slot.get("dosage_weight") != null ? 
                new BigDecimal(slot.get("dosage_weight").toString()) : new BigDecimal("1.0");
            
            BigDecimal currentWeight = slot.get("current_weight") != null ? 
                new BigDecimal(slot.get("current_weight").toString()) : BigDecimal.ZERO;
            
            int expectedDosage = dosageWeight != null ? dosageWeight.intValue() : 1;
            if (userMedicineId != null) {
                BigDecimal dosagePerTime = getDosagePerTime(userMedicineId);
                if (dosagePerTime != null) {
                    expectedDosage = dosagePerTime.intValue();
                }
            }
            
            BigDecimal weightBeforeTake = slot.get("weight_before_take") != null ? 
                new BigDecimal(slot.get("weight_before_take").toString()) : null;
            BigDecimal weightAfterTake = slot.get("weight_after_take") != null ? 
                new BigDecimal(slot.get("weight_after_take").toString()) : null;
            
            BigDecimal newWeight = currentWeight.subtract(weightChange).max(BigDecimal.ZERO);
            
            if (weightBeforeTake == null) {
                weightBeforeTake = currentWeight;
            }
            weightAfterTake = newWeight;
            
            jdbcTemplate.update(
                "UPDATE medicine_box_slot SET current_weight = ?, weight_before_take = ?, weight_after_take = ?, update_time = NOW() WHERE id = ?",
                newWeight, weightBeforeTake, weightAfterTake, slotIdLong
            );
            
            BigDecimal totalTakenWeight = weightBeforeTake.subtract(weightAfterTake).max(BigDecimal.ZERO);
            
            Map<String, Object> result = new HashMap<>();
            result.put("weightChange", weightChange);
            result.put("medicineName", medicineName);
            result.put("expectedDosage", expectedDosage);
            result.put("slotId", slotIdLong);
            result.put("userMedicineId", userMedicineId);
            result.put("inMedicationWindow", inMedicationWindow);
            result.put("totalTakenWeight", totalTakenWeight);
            result.put("weightBeforeTake", weightBeforeTake);
            
            if (windowInfo != null) {
                result.put("plannedTime", windowInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                result.put("earlyAllowTime", windowInfo.earlyAllowTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                result.put("makeupDeadTime", windowInfo.makeupDeadTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                result.put("isFirstDose", windowInfo.isFirstDose);
                
                LocalTime nowCheck = LocalTime.now();
                log.info("[时间窗口-最终检查] userMedicineId={}, 当前时间={}, 提前允许={}, 补服截止={}, inMedicationWindow={}", 
                    userMedicineId, nowCheck, windowInfo.earlyAllowTime, windowInfo.makeupDeadTime, inMedicationWindow);
                
                boolean isAfterEarly = !nowCheck.isBefore(windowInfo.earlyAllowTime);
                boolean isBeforeDeadline = !nowCheck.isAfter(windowInfo.makeupDeadTime);
                log.info("[时间窗口-详细判断] isAfterEarly={}, isBeforeDeadline={}", isAfterEarly, isBeforeDeadline);
                
                if (isAfterEarly && isBeforeDeadline) {
                    log.info("[时间窗口-强制修复] 明明在时间窗口内！强制允许服药！");
                    inMedicationWindow = true;
                }
            }
            
            if (!inMedicationWindow) {
                String reason = determineNotInWindowReason(windowInfo);
                result.put("wrongTime", true);
                result.put("needRestockAll", true);
                result.put("reason", reason);
                
                if ("too_early".equals(reason)) {
                    result.put("message", "距离计划服药时间还太早，请将药品放回");
                    result.put("detailMessage", "计划服药时间为 " + (windowInfo != null ? windowInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")) : "") + 
                        "，可在 " + (windowInfo != null ? windowInfo.earlyAllowTime.format(DateTimeFormatter.ofPattern("HH:mm")) : "") + " 之后服用。");
                } else {
                    result.put("message", "已错过补服时间，请将药品放回");
                    result.put("detailMessage", "补服截止时间为 " + (windowInfo != null ? windowInfo.makeupDeadTime.format(DateTimeFormatter.ofPattern("HH:mm")) : "") + 
                        "，已错过本次服药窗口，请将药品放回。");
                }
                
                result.put("requiredRestockWeight", weightChange);
                result.put("currentRestockedWeight", BigDecimal.ZERO);
                log.info("[非服药时间取药] userId={}, 取出重量={}g, 原因={}, 需全部放回", userId, weightChange, reason);
                
                recordWrongTimeOperation(userId, userMedicineId, slotIdLong, weightChange, "takeout");
                
                mqttController.sendDeviceCommand(deviceId, "wrong_time", 8, "red", "alternate");
                
                return Result.success(result);
            }
            
            Map<String, Object> box = getBoxById(boxId);
            BigDecimal expectedDosageWeight = box != null && box.get("single_dose_weight") != null ? 
                new BigDecimal(box.get("single_dose_weight").toString()) : dosageWeight;
            BigDecimal toleranceMin = box != null && box.get("tolerance_min") != null ? 
                new BigDecimal(box.get("tolerance_min").toString()) : expectedDosageWeight.multiply(new BigDecimal("0.85"));
            BigDecimal toleranceMax = box != null && box.get("tolerance_max") != null ? 
                new BigDecimal(box.get("tolerance_max").toString()) : expectedDosageWeight.multiply(new BigDecimal("1.15"));
            
            Result dosageVerifyResult = verifyMedicationDosageByWeight(weightBeforeTake, weightAfterTake, expectedDosageWeight, toleranceMin, toleranceMax);
            if (dosageVerifyResult != null && dosageVerifyResult.getData() != null) {
                Map<String, Object> dosageData = (Map<String, Object>) dosageVerifyResult.getData();
                result.put("dosageVerification", dosageData);
                
                String takeResult = (String) dosageData.get("takeResult");
                if ("underdose".equals(takeResult) || "overdose".equals(takeResult)) {
                    result.put("message", dosageData.get("message"));
                    if ("underdose".equals(takeResult)) {
                        result.put("needMore", true);
                        startEmergencyTimer(userId, deviceId, slotIdLong, weightBefore, weightAfter, weightChange, "underdose");
                        mqttController.sendDeviceCommand(deviceId, "medicine_underdose", 10, "yellow", "blink_slow");
                    } else {
                        result.put("needRestock", true);
                        startEmergencyTimer(userId, deviceId, slotIdLong, weightBefore, weightAfter, weightChange, "overdose");
                        mqttController.sendDeviceCommand(deviceId, "medicine_overdose", 7, "red", "alternate");
                    }
                    log.info("[剂量验证-异常] userId={}, takeResult={}", userId, takeResult);
                    return Result.success(result);
                }
            }
            
            String medicationStatus = determineMedicationStatus(windowInfo);
            result.put("medicationStatus", medicationStatus);
            
            int actualTakenInPills = 0;
            if (expectedDosageWeight != null && expectedDosageWeight.compareTo(BigDecimal.ZERO) > 0) {
                actualTakenInPills = totalTakenWeight.divide(expectedDosageWeight, 0, RoundingMode.HALF_UP).intValue();
            }
            
            completeMedicationSession(userId, userMedicineId != null ? userMedicineId.longValue() : null, 
                slotIdLong, actualTakenInPills, expectedDosage, weightBefore, weightAfter, weightChange);
            
            result.put("takeSuccess", true);
            result.put("message", "服药成功");
            result.put("detailMessage", "您已正确服用药品。");
            
            log.info("[剂量验证-正常] userId={}, 已取={}g, 预期={}g", userId, totalTakenWeight, expectedDosageWeight);
            mqttController.sendDeviceCommand(deviceId, "medicine_success", 3, "green");
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("[取药事件处理失败]", e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }
    
    private void recordWrongTimeOperation(Long userId, Integer userMedicineId, Long slotId, 
                                           BigDecimal amount, String operationType) {
        try {
            jdbcTemplate.update(
                "INSERT INTO box_events (device_id, user_id, event_type, medicine_id, event_time, payload) " +
                "SELECT device_id, ?, ?, ?, NOW(), ? FROM medicine_box_slot s " +
                "JOIN medicine_box b ON s.box_id = b.id WHERE s.id = ?",
                userId, operationType, userMedicineId,
                "{\"amount\": " + amount + ", \"type\": \"wrong_time_operation\"}",
                slotId
            );
            log.info("[记录非服药时间操作] userId={}, operationType={}, amount={}", userId, operationType, amount);
        } catch (Exception e) {
            log.error("记录非服药时间操作失败", e);
        }
    }
    
    private void handleBlockedTimeTimeout(Long userId, Integer userMedicineId, Long slotId, 
                                          BigDecimal totalTakenWeight, int expectedDosage) {
        try {
            int actualTaken = totalTakenWeight.setScale(0, RoundingMode.HALF_UP).intValue();
            if (actualTaken <= 0) {
                log.info("[非服药时间超时] 药品已放回，无需扣减库存");
                return;
            }
            
            jdbcTemplate.update(
                "UPDATE user_medicines SET current_stock = GREATEST(0, current_stock - ?), update_time = NOW() WHERE id = ?",
                actualTaken, userMedicineId
            );
            
            jdbcTemplate.update(
                "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, taken_time, dosage_taken, " +
                "expected_dosage, status, take_result, source, create_time) VALUES (?, ?, NOW(), NOW(), ?, ?, 'taken', 'blocked_not_returned', 'device', NOW())",
                userMedicineId, slotId, actualTaken, expectedDosage
            );
            
            log.info("[非服药时间超时-扣减库存] userId={}, userMedicineId={}, actualTaken={}", userId, userMedicineId, actualTaken);
        } catch (Exception e) {
            log.error("[非服药时间超时处理失败]", e);
        }
    }
    
    private BigDecimal getDosagePerTime(Integer userMedicineId) {
        if (userMedicineId == null) return null;
        try {
            Object dosage = jdbcTemplate.queryForObject(
                "SELECT dosage_per_time FROM user_medicines WHERE id = ?",
                Object.class, userMedicineId
            );
            if (dosage != null) {
                return new BigDecimal(dosage.toString());
            }
        } catch (Exception e) {
            log.warn("获取单次剂量失败, userMedicineId={}", userMedicineId);
        }
        return null;
    }
    
    private String judgeCumulativeTakeResult(int actualTake, int expectedDosage) {
        if (actualTake < expectedDosage) {
            return "underdose";
        } else if (actualTake > expectedDosage) {
            return "overdose";
        }
        return "normal";
    }
    
    private String determineNotInWindowReason(MedicationWindowInfo windowInfo) {
        if (windowInfo == null) return "unknown";
        
        LocalTime now = LocalTime.now();
        
        if (now.isBefore(windowInfo.earlyAllowTime)) {
            return "too_early";
        }
        
        if (now.isAfter(windowInfo.makeupDeadTime)) {
            return "missed";
        }
        
        return "unknown";
    }
    
    private String determineMedicationStatus(MedicationWindowInfo windowInfo) {
        if (windowInfo == null) return "normal";
        
        LocalTime now = LocalTime.now();
        
        if (now.isBefore(windowInfo.plannedTime)) {
            return "early";
        }
        
        long minutesSincePlanned = java.time.Duration.between(windowInfo.plannedTime, now).toMinutes();
        
        if (minutesSincePlanned <= 30) {
            return "normal";
        }
        
        return "makeup";
    }
    
    private void completeMedicationSession(Long userId, Long medicineId, Long slotId, int actualTake, int expectedDosage,
                                           BigDecimal weightBefore, BigDecimal weightAfter, BigDecimal weightDiff) {
        stopEmergencyTimer(getDeviceIdBySlotId(slotId), slotId);
        
        jdbcTemplate.update(
            "UPDATE medicine_box_slot SET actual_take_count = 0, expected_take_count = ?, take_match_status = 'match', " +
            "weight_before_take = NULL, weight_after_take = NULL, update_time = NOW() WHERE id = ?",
            expectedDosage, slotId
        );
        
        if (medicineId != null) {
            jdbcTemplate.update(
                "UPDATE user_medicines SET current_stock = GREATEST(0, current_stock - ?), update_time = NOW() WHERE id = ?",
                actualTake, medicineId
            );
            
            MedicationWindowInfo windowInfo = getMedicationWindowInfo(medicineId.intValue());
            String status = "taken";
            String takeResult = "normal";
            
            if (windowInfo != null) {
                LocalTime now = LocalTime.now();
                
                if (now.isBefore(windowInfo.plannedTime)) {
                    status = "taken_early";
                    takeResult = "early";
                } else {
                    long minutesSincePlanned = java.time.Duration.between(windowInfo.plannedTime, now).toMinutes();
                    if (minutesSincePlanned > 30) {
                        status = "taken_makeup";
                        takeResult = "makeup";
                    }
                }
            }
            
            jdbcTemplate.update(
                "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, taken_time, dosage_taken, " +
                "weight_before, weight_after, weight_diff, expected_dosage, take_result, status, source, create_time) " +
                "VALUES (?, ?, NOW(), NOW(), ?, ?, ?, ?, ?, ?, 'taken', 'device', NOW())",
                medicineId, slotId, actualTake, weightBefore, weightAfter, weightDiff, expectedDosage, takeResult, status
            );
        }
        
        log.info("[完成服药会话] userId={}, medicineId={}, actualTake={}, expectedDosage={}", userId, medicineId, actualTake, expectedDosage);
        
        if (medicineId != null) {
            checkMedicineDepleted(userId, medicineId.intValue());
        }
    }
    
    private void recordMedicationLogWithStatus(Long userId, Integer userMedicineId, Long slotId, 
                                               int actualTake, int expectedDosage,
                                               BigDecimal weightBefore, BigDecimal weightAfter, BigDecimal weightDiff,
                                               String takeResult, String status) {
        if (userMedicineId == null) {
            return;
        }
        
        try {
            jdbcTemplate.update(
                "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, taken_time, dosage_taken, " +
                "weight_before, weight_after, weight_diff, expected_dosage, take_result, status, source, create_time) " +
                "VALUES (?, ?, NOW(), NOW(), ?, ?, ?, ?, ?, ?, ?, 'device', NOW())",
                userMedicineId, slotId, actualTake, weightBefore, weightAfter, weightDiff, expectedDosage, takeResult, status
            );
            
            jdbcTemplate.update(
                "UPDATE user_medicines SET current_stock = GREATEST(0, current_stock - ?), update_time = NOW() WHERE id = ?",
                actualTake, userMedicineId
            );
            
            log.info("[记录服药日志] userId={}, medicineId={}, actualTake={}, expectedDosage={}, takeResult={}, status={}", 
                userId, userMedicineId, actualTake, expectedDosage, takeResult, status);
        } catch (Exception e) {
            log.error("[记录服药日志失败]", e);
        }
    }
    
    private void checkMedicineDepleted(Long userId, Integer userMedicineId) {
        try {
            Integer currentStock = jdbcTemplate.queryForObject(
                "SELECT current_stock FROM user_medicines WHERE id = ?", Integer.class, userMedicineId
            );
            
            Integer dosagePerTime = jdbcTemplate.queryForObject(
                "SELECT dosage_per_time FROM user_medicines WHERE id = ?", Integer.class, userMedicineId
            );
            
            Integer frequencyPerDay = getFrequencyPerDay(userMedicineId.longValue());
            
            String deviceId = getDeviceIdByUserId(userId);
            
            int remainingDays = 0;
            if (dosagePerTime != null && dosagePerTime > 0 && frequencyPerDay != null && frequencyPerDay > 0) {
                remainingDays = currentStock / (dosagePerTime * frequencyPerDay);
            } else if (dosagePerTime != null && dosagePerTime > 0) {
                remainingDays = currentStock / dosagePerTime;
            }
            
            if (currentStock != null && currentStock <= 0) {
                String medicineName = jdbcTemplate.queryForObject(
                    "SELECT custom_name FROM user_medicines WHERE id = ?", String.class, userMedicineId
                );
                
                log.info("[药品用完检测] userId={}, medicineId={}, 当前库存为0", userId, userMedicineId);
                
                String content = String.format("您的药品 %s 已用完，请及时补充！", medicineName != null ? medicineName : "");
                notifyFamilyMembers(userId, "medicine_depleted", medicineName != null ? medicineName : "药品", getElderName(userId), content);
                
                recordBoxEvent(deviceId, userId, "medicine_depleted", null, null, null, null);
                
                sendVoicePrompt(userId, "您的药品已用完，请及时补充");
                
                if (deviceId != null) {
                    sendLowStockAlert(deviceId);
                }
            } else if (currentStock != null && remainingDays <= 2) {
                log.info("[低库存检测] userId={}, medicineId={}, 当前库存={}, 剩余天数={}", 
                    userId, userMedicineId, currentStock, remainingDays);
                
                if (deviceId != null) {
                    sendLowStockAlert(deviceId);
                }
            }
        } catch (Exception e) {
            log.error("[药品用完检测失败]", e);
        }
    }
    
    private int getFrequencyPerDay(Long medicineId) {
        try {
            Integer freq = jdbcTemplate.queryForObject(
                "SELECT frequency_per_day FROM user_medicines WHERE id = ?", Integer.class, medicineId
            );
            return freq != null ? freq : 2;
        } catch (Exception e) {
            return 2;
        }
    }
    
    private Map<String, Object> checkTakenTodayByUserMedicineAndTime(Integer userMedicineId, LocalDateTime scheduledTime) {
        try {
            String sql = "SELECT ml.id, ml.taken_time, ml.scheduled_time, ml.status " +
                        "FROM medication_logs ml " +
                        "WHERE ml.user_medicine_id = ? " +
                        "AND (ml.status = 'taken' OR ml.status = 'taken_early' OR ml.status = 'taken_makeup') " +
                        "AND ml.scheduled_time = ? " +
                        "LIMIT 1";
            
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql, userMedicineId, scheduledTime);
            
            return logs.isEmpty() ? null : logs.get(0);
        } catch (Exception e) {
            log.error("检查重复服药失败", e);
            return null;
        }
    }
    
    private String getDeviceIdByUserId(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT device_id FROM medicine_box WHERE user_id = ?", String.class, userId
            );
        } catch (Exception e) {
            return null;
        }
    }

    @Transactional
    public Result handleRefillEvent(String deviceId, Long userId, Long boxId, Integer slotId,
                                    BigDecimal weightBefore, BigDecimal weightAfter, BigDecimal addedWeight) {
        try {
            Map<String, Object> slot = getSlotByBoxAndNumber(boxId, slotId);
            if (slot == null) {
                return Result.error("药仓不存在");
            }
            
            BigDecimal weightBeforeTake = slot.get("weight_before_take") != null ? 
                new BigDecimal(slot.get("weight_before_take").toString()) : null;
            
            if (weightBeforeTake == null) {
                log.info("[纯加药] deviceId={}, 添加重量={}g", deviceId, addedWeight);
                mqttController.sendDeviceCommand(deviceId, "refill_success", 9, "blue");
            }

            Long slotIdLong = ((Number) slot.get("id")).longValue();
            Integer userMedicineId = slot.get("user_medicine_id") != null ? 
                ((Number) slot.get("user_medicine_id")).intValue() : null;
            String medicineName = (String) slot.get("slot_name");
            BigDecimal dosageWeight = slot.get("dosage_weight") != null ? 
                new BigDecimal(slot.get("dosage_weight").toString()) : new BigDecimal("1.0");
            
            BigDecimal currentWeight = slot.get("current_weight") != null ? 
                new BigDecimal(slot.get("current_weight").toString()) : BigDecimal.ZERO;
            
            int expectedDosage = dosageWeight != null ? dosageWeight.intValue() : 1;
            if (userMedicineId != null) {
                BigDecimal dosagePerTime = getDosagePerTime(userMedicineId);
                if (dosagePerTime != null) {
                    expectedDosage = dosagePerTime.intValue();
                }
            }
            
            boolean inMedicationWindow = userMedicineId != null && isInMedicationWindow(userMedicineId);
            
            BigDecimal weightAfterTake = slot.get("weight_after_take") != null ? 
                new BigDecimal(slot.get("weight_after_take").toString()) : null;
            
            BigDecimal totalTakenWeight = BigDecimal.ZERO;
            BigDecimal currentRestockedWeight = BigDecimal.ZERO;
            
            if (weightBeforeTake != null && weightAfterTake != null) {
                totalTakenWeight = weightBeforeTake.subtract(weightAfterTake).max(BigDecimal.ZERO);
                currentRestockedWeight = currentWeight.subtract(weightAfterTake).max(BigDecimal.ZERO);
            }
            
            BigDecimal newRestockedWeight = currentRestockedWeight.add(addedWeight);
            BigDecimal remainingToRestock = totalTakenWeight.subtract(newRestockedWeight).max(BigDecimal.ZERO);
            
            BigDecimal newWeight = currentWeight.add(addedWeight);
            
            jdbcTemplate.update(
                "UPDATE medicine_box_slot SET current_weight = ?, update_time = NOW() WHERE id = ?",
                newWeight, slotIdLong
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("addedWeight", addedWeight);
            result.put("medicineName", medicineName);
            result.put("slotId", slotIdLong);
            result.put("userMedicineId", userMedicineId);
            result.put("totalTakenWeight", totalTakenWeight);
            result.put("currentRestockedWeight", newRestockedWeight);
            result.put("remainingToRestock", remainingToRestock);
            result.put("inMedicationWindow", inMedicationWindow);
            
            if (!inMedicationWindow && totalTakenWeight.compareTo(BigDecimal.ZERO) > 0) {
                if (newRestockedWeight.compareTo(totalTakenWeight) < 0) {
                    result.put("wrongTime", true);
                    result.put("needRestockAll", true);
                    result.put("restockIncomplete", true);
                    result.put("message", "放回数量不正确，请继续放回");
                    result.put("detailMessage", "您取出了 " + totalTakenWeight + "g 药品，已放回 " + newRestockedWeight + "g，还需放回 " + remainingToRestock + "g。");
                    log.info("[非服药时间放回-不完整] userId={}, 取出={}g, 已放回={}g, 还需放回={}g", 
                        userId, totalTakenWeight, newRestockedWeight, remainingToRestock);
                    return Result.success(result);
                }
                
                jdbcTemplate.update(
                    "UPDATE medicine_box_slot SET weight_before_take = NULL, weight_after_take = NULL, actual_take_count = 0, update_time = NOW() WHERE id = ?",
                    slotIdLong
                );
                
                result.put("restockComplete", true);
                result.put("message", "药品已全部放回");
                result.put("detailMessage", "您已成功放回全部 " + totalTakenWeight + "g 药品，请按时服药。");
                log.info("[非服药时间放回-完成] userId={}, 取出={}g, 已放回={}g", userId, totalTakenWeight, newRestockedWeight);
                return Result.success(result);
            }
            
            Map<String, Object> box = getBoxById(boxId);
            BigDecimal expectedDosageWeight = box != null && box.get("single_dose_weight") != null ? 
                new BigDecimal(box.get("single_dose_weight").toString()) : dosageWeight;
            BigDecimal toleranceMin = box != null && box.get("tolerance_min") != null ? 
                new BigDecimal(box.get("tolerance_min").toString()) : expectedDosageWeight.multiply(new BigDecimal("0.85"));
            BigDecimal toleranceMax = box != null && box.get("tolerance_max") != null ? 
                new BigDecimal(box.get("tolerance_max").toString()) : expectedDosageWeight.multiply(new BigDecimal("1.15"));
            
            if (weightBeforeTake != null) {
                Result dosageVerifyResult = verifyDosageWithRefill(
                    weightBeforeTake, weightAfterTake, 
                    weightBefore, weightAfter, 
                    expectedDosageWeight, toleranceMin, toleranceMax
                );
                if (dosageVerifyResult != null && dosageVerifyResult.getData() != null) {
                    Map<String, Object> dosageData = (Map<String, Object>) dosageVerifyResult.getData();
                    result.put("dosageVerification", dosageData);
                    
                    String takeResult = (String) dosageData.get("takeResult");
                    if ("underdose".equals(takeResult) || "overdose".equals(takeResult)) {
                        result.put("message", dosageData.get("message"));
                        if ("underdose".equals(takeResult)) {
                            result.put("needMore", true);
                        } else {
                            result.put("needRestock", true);
                        }
                        log.info("[净剂量验证-异常] userId={}, takeResult={}", userId, takeResult);
                        return Result.success(result);
                    }
                }
            }
            
            int actualTakenInPills = 0;
            if (expectedDosageWeight != null && expectedDosageWeight.compareTo(BigDecimal.ZERO) > 0) {
                actualTakenInPills = totalTakenWeight.divide(expectedDosageWeight, 0, RoundingMode.HALF_UP).intValue();
            }
            
            jdbcTemplate.update(
                "UPDATE medicine_box_slot SET weight_before_take = NULL, weight_after_take = NULL, update_time = NOW() WHERE id = ?",
                slotIdLong
            );
            
            int restockedInPills = 0;
            if (expectedDosageWeight != null && expectedDosageWeight.compareTo(BigDecimal.ZERO) > 0) {
                restockedInPills = addedWeight.divide(expectedDosageWeight, 0, RoundingMode.HALF_UP).intValue();
            }
            
            if (userMedicineId != null && restockedInPills > 0) {
                jdbcTemplate.update(
                    "UPDATE user_medicines SET current_stock = current_stock + ?, update_time = NOW() WHERE id = ?",
                    restockedInPills, userMedicineId
                );
            }
            
            completeMedicationSession(userId, userMedicineId != null ? userMedicineId.longValue() : null, 
                slotIdLong, actualTakenInPills, expectedDosage, weightBefore, weightAfter, addedWeight);
            
            result.put("takeSuccess", true);
            result.put("message", "服药成功");
            result.put("detailMessage", "您已正确服用药品。");
            
            log.info("[放回后-正常] userId={}, 已取={}g, 预期={}g", userId, totalTakenWeight, expectedDosageWeight);
            mqttController.sendDeviceCommand(deviceId, "medicine_success", 3, "green");
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("[补药事件处理失败]", e);
            return Result.error("处理失败: " + e.getMessage());
        }
    }

    public Result handleBottleRemoved(Map<String, Object> message) {
        String deviceId = (String) message.get("deviceId");
        Long userId = getUserIdByDeviceId(deviceId);

        DeviceState state = deviceStates.computeIfAbsent(deviceId, k -> new DeviceState());
        state.bottlePresent = false;
        state.bottleRemovedTime = LocalDateTime.now();

        recordBoxEvent(deviceId, userId, "bottle_removed", null, null, null, null);
        
        if (userId != null) {
            sendDeviceEventToElder(userId, "BOTTLE_REMOVED", "药瓶已从药盒取出", null, "请妥善保管药瓶，不要丢失");
        }

        log.info("[药瓶移除] deviceId={}", deviceId);
        return Result.success("药瓶移除事件已记录");
    }

    public Result handleBottleReturned(Map<String, Object> message) {
        String deviceId = (String) message.get("deviceId");
        Long userId = getUserIdByDeviceId(deviceId);
        BigDecimal weightAfter = message.get("weightAfter") != null ? 
            new BigDecimal(message.get("weightAfter").toString()) : BigDecimal.ZERO;

        DeviceState state = deviceStates.computeIfAbsent(deviceId, k -> new DeviceState());
        state.bottlePresent = true;
        state.bottleRemovedTime = null;

        recordBoxEvent(deviceId, userId, "bottle_returned", null, null, null, null);

        Long boxId = getBoxIdByDeviceId(deviceId);
        if (boxId != null) {
            Map<String, Object> slot = getSlotByBoxAndNumber(boxId, 1);
            if (slot != null) {
                jdbcTemplate.update(
                    "UPDATE medicine_box_slot SET current_weight = ?, update_time = NOW() WHERE id = ?",
                    weightAfter, ((Number) slot.get("id")).longValue()
                );
            }
        }
        
        if (userId != null) {
            sendDeviceEventToElder(userId, "BOTTLE_RETURNED", "药瓶已放回药盒", null, "很好！药瓶已安全放回");
        }

        log.info("[药瓶放回] deviceId={}, weight={}", deviceId, weightAfter);
        return Result.success("药瓶放回事件已记录");
    }

    public Result handleHeartbeat(Map<String, Object> message) {
        String deviceId = (String) message.get("deviceId");
        BigDecimal weight = message.get("weight") != null ? 
            new BigDecimal(message.get("weight").toString()) : BigDecimal.ZERO;
        Boolean bottlePresent = (Boolean) message.get("bottlePresent");

        DeviceState state = deviceStates.computeIfAbsent(deviceId, k -> new DeviceState());
        state.lastWeight = weight;
        state.lastUpdateTime = LocalDateTime.now();
        if (bottlePresent != null) {
            state.bottlePresent = bottlePresent;
        }

        updateDeviceOnlineStatus(deviceId, true);

        return Result.success("心跳已记录");
    }

    public Result handleTakeoutEvent(Map<String, Object> message) {
        String deviceId = (String) message.get("deviceId");
        Integer slotId = message.get("slotId") != null ? 
            ((Number) message.get("slotId")).intValue() : 1;
        BigDecimal weightBefore = message.get("weightBefore") != null ? 
            new BigDecimal(message.get("weightBefore").toString()) : BigDecimal.ZERO;
        BigDecimal weightAfter = message.get("weightAfter") != null ? 
            new BigDecimal(message.get("weightAfter").toString()) : BigDecimal.ZERO;
        BigDecimal weightChange = message.get("weightChange") != null ? 
            new BigDecimal(message.get("weightChange").toString()) : weightBefore.subtract(weightAfter);

        Long userId = getUserIdByDeviceId(deviceId);
        Long boxId = getBoxIdByDeviceId(deviceId);

        recordBoxEvent(deviceId, userId, "takeout", slotId, weightBefore, weightAfter, weightChange);
        
        String medicineName = null;
        if (boxId != null) {
            Map<String, Object> slot = getSlotByBoxAndNumber(boxId, slotId);
            if (slot != null) {
                medicineName = (String) slot.get("slot_name");
            }
        }
        
        if (userId != null) {
            String details = weightChange != null ? "取出约 " + weightChange.setScale(1, RoundingMode.HALF_UP) + " 克" : "";
            sendDeviceEventToElder(userId, "TAKEOUT", "已从药盒取出药品", medicineName, details);
        }

        log.info("[取药事件] deviceId={}, slotId={}, weightChange={}g", deviceId, slotId, weightChange);

        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("slotId", slotId);
        result.put("weightChange", weightChange);
        result.put("message", "取药事件已记录");

        return Result.success(result);
    }

    public Result handlePutbackEvent(Map<String, Object> message) {
        String deviceId = (String) message.get("deviceId");
        Integer slotId = message.get("slotId") != null ? 
            ((Number) message.get("slotId")).intValue() : 1;
        BigDecimal weightBefore = message.get("weightBefore") != null ? 
            new BigDecimal(message.get("weightBefore").toString()) : BigDecimal.ZERO;
        BigDecimal weightAfter = message.get("weightAfter") != null ? 
            new BigDecimal(message.get("weightAfter").toString()) : BigDecimal.ZERO;
        BigDecimal weightChange = message.get("weightChange") != null ? 
            new BigDecimal(message.get("weightChange").toString()) : weightAfter.subtract(weightBefore);

        Long userId = getUserIdByDeviceId(deviceId);
        Long boxId = getBoxIdByDeviceId(deviceId);

        recordBoxEvent(deviceId, userId, "putback", slotId, weightBefore, weightAfter, weightChange);
        
        String medicineName = null;
        if (boxId != null) {
            Map<String, Object> slot = getSlotByBoxAndNumber(boxId, slotId);
            if (slot != null) {
                medicineName = (String) slot.get("slot_name");
            }
        }
        
        if (userId != null) {
            String details = weightChange != null ? "放回约 " + weightChange.setScale(1, RoundingMode.HALF_UP) + " 克" : "";
            sendDeviceEventToElder(userId, "PUTBACK", "已将药品放回药盒", medicineName, details);
        }

        log.info("[放回事件] deviceId={}, slotId={}, weightChange={}g", deviceId, slotId, weightChange);

        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("slotId", slotId);
        result.put("weightChange", weightChange);
        result.put("message", "放回事件已记录");

        return Result.success(result);
    }

    public Result handleLowStockEvent(Map<String, Object> message) {
        String deviceId = (String) message.get("deviceId");
        Integer slotId = message.get("slotId") != null ? 
            ((Number) message.get("slotId")).intValue() : 1;
        Integer currentStock = message.get("currentStock") != null ? 
            ((Number) message.get("currentStock")).intValue() : 0;
        Integer threshold = message.get("threshold") != null ? 
            ((Number) message.get("threshold")).intValue() : 7;

        Long userId = getUserIdByDeviceId(deviceId);
        Long boxId = getBoxIdByDeviceId(deviceId);

        recordBoxEvent(deviceId, userId, "low_stock", slotId, null, null, null);
        
        String medicineName = null;
        if (boxId != null) {
            Map<String, Object> slot = getSlotByBoxAndNumber(boxId, slotId);
            if (slot != null) {
                medicineName = (String) slot.get("slot_name");
            }
        }
        
        if (userId != null) {
            String details = "当前剩余 " + currentStock + " 天用量";
            sendDeviceEventToElder(userId, "LOW_STOCK", "药品库存不足", medicineName, details);
        }

        String content = String.format("您的智能药盒药品库存不足，当前剩余 %d 天用量，请及时补充！", currentStock);
        notifyFamilyMembers(userId, "low_stock", "药品", "用户", content);

        log.info("[低库存警告] deviceId={}, slotId={}, currentStock={}", deviceId, slotId, currentStock);

        Map<String, Object> result = new HashMap<>();
        result.put("deviceId", deviceId);
        result.put("slotId", slotId);
        result.put("currentStock", currentStock);
        result.put("threshold", threshold);
        result.put("message", "低库存警告已发送");

        return Result.success(result);
    }

    public Result initializeDevice(String deviceId, Long userId, BigDecimal fullBottleWeight, 
                                   BigDecimal singleDoseWeight, Integer pillsPerBottle) {
        try {
            Long boxId = getBoxIdByDeviceId(deviceId);
            if (boxId == null) {
                jdbcTemplate.update(
                    "INSERT INTO medicine_box (user_id, device_id, box_name, status, battery_level, last_sync_time, create_time) " +
                    "VALUES (?, ?, '智能药盒', 1, 100, NOW(), NOW())",
                    userId, deviceId
                );
                boxId = getBoxIdByDeviceId(deviceId);
                initBoxSlots(boxId);
            }

            BigDecimal toleranceMin = null;
            BigDecimal toleranceMax = null;
            
            if (singleDoseWeight != null) {
                BigDecimal tolerance = singleDoseWeight.multiply(new BigDecimal("0.15"));
                toleranceMin = singleDoseWeight.subtract(tolerance);
                toleranceMax = singleDoseWeight.add(tolerance);
            }

            jdbcTemplate.update(
                "UPDATE medicine_box SET single_dose_weight = ?, full_bottle_weight = ?, pills_per_bottle = ?, " +
                "tolerance_min = ?, tolerance_max = ?, initialized = 1, init_step = NULL, " +
                "last_sync_time = NOW(), update_time = NOW() WHERE device_id = ?",
                singleDoseWeight, fullBottleWeight, pillsPerBottle, toleranceMin, toleranceMax, deviceId
            );

            DeviceState state = deviceStates.computeIfAbsent(deviceId, k -> new DeviceState());
            state.initialized = true;
            state.fullBottleWeight = fullBottleWeight;
            state.singleDoseWeight = singleDoseWeight;
            state.pillsPerBottle = pillsPerBottle;
            state.toleranceMin = toleranceMin;
            state.toleranceMax = toleranceMax;
            state.initStep = null;

            recordBoxEvent(deviceId, userId, "init_complete", null, 
                fullBottleWeight, null, singleDoseWeight);

            log.info("[设备初始化完成] deviceId={}, fullBottleWeight={}, singleDoseWeight={}, tolerance={}~{}", 
                deviceId, fullBottleWeight, singleDoseWeight, toleranceMin, toleranceMax);

            Map<String, Object> result = new HashMap<>();
            result.put("fullBottleWeight", fullBottleWeight);
            result.put("singleDoseWeight", singleDoseWeight);
            result.put("toleranceMin", toleranceMin);
            result.put("toleranceMax", toleranceMax);
            result.put("message", "初始化成功！");
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("[设备初始化失败]", e);
            return Result.error("初始化失败: " + e.getMessage());
        }
    }

    public Result startInitialization(String deviceId, Long userId, String step) {
        try {
            DeviceState state = deviceStates.computeIfAbsent(deviceId, k -> new DeviceState());
            state.initUserId = userId;

            if ("weigh_full_bottle".equals(step) || "step1".equals(step)) {
                state.initStep = "1";
                state.fullBottleWeight = null;
                state.singleDoseWeight = null;
                state.toleranceMin = null;
                state.toleranceMax = null;
                state.initialized = false;

                jdbcTemplate.update(
                    "UPDATE medicine_box SET init_step = ?, initialized = 0, " +
                    "single_dose_weight = NULL, full_bottle_weight = NULL, " +
                    "tolerance_min = NULL, tolerance_max = NULL, " +
                    "second_weighing_weight = NULL, init_first_weight = NULL, update_time = NOW() WHERE device_id = ?",
                    1, deviceId
                );

                log.info("[初始化步骤1启动] 等待称量完整药品, deviceId={}", deviceId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("step", 1);
                result.put("message", "步骤1：请将完整药品（如罗红霉素整盒）放在药盒上，系统将自动记录重量。");
                result.put("instruction", "确保药品放置稳定，等待系统确认称量完成。");
                return Result.success(result);
            } 
            else if ("weigh_single_dose".equals(step) || "step2".equals(step)) {
                if (state == null || state.fullBottleWeight == null) {
                    return Result.error("请先完成完整药品称量（步骤1）！");
                }
                state.initStep = "2";

                jdbcTemplate.update(
                    "UPDATE medicine_box SET init_step = ?, update_time = NOW() WHERE device_id = ?",
                    2, deviceId
                );

                log.info("[初始化步骤2启动] 等待称量剩余药品, deviceId={}", deviceId);
                
                Map<String, Object> result = new HashMap<>();
                result.put("step", 2);
                result.put("fullBottleWeight", state.fullBottleWeight);
                result.put("message", "步骤2：请从完整药品中取出一次用药的标准剂量后，将剩余药品再次放置在药盒上。");
                result.put("instruction", "系统将通过两次称量的差值计算出单次用药的实际克数，并计算容忍度标准。");
                return Result.success(result);
            }
            else if ("cancel".equals(step)) {
                state.initStep = null;
                state.fullBottleWeight = null;
                state.singleDoseWeight = null;
                state.toleranceMin = null;
                state.toleranceMax = null;

                jdbcTemplate.update(
                    "UPDATE medicine_box SET init_step = NULL, update_time = NOW() WHERE device_id = ?",
                    deviceId
                );

                log.info("[初始化取消] deviceId={}", deviceId);
                return Result.success("初始化已取消");
            }

            return Result.error("未知初始化步骤");
        } catch (Exception e) {
            log.error("[初始化失败]", e);
            return Result.error("初始化失败: " + e.getMessage());
        }
    }

    private void startEmergencyTimer(Long userId, String deviceId, Long slotId, 
                                     BigDecimal weightBefore, BigDecimal weightAfter, 
                                     BigDecimal weightChange, String takeResult) {
        String timerKey = deviceId + "_" + slotId;
        
        EmergencyTimer existingTimer = emergencyTimers.get(timerKey);
        if (existingTimer != null && existingTimer.isActive) {
            return;
        }

        EmergencyTimer timer = new EmergencyTimer();
        timer.userId = userId;
        timer.deviceId = deviceId;
        timer.slotId = slotId;
        timer.weightBefore = weightBefore;
        timer.weightAfter = weightAfter;
        timer.weightChange = weightChange;
        timer.takeResult = takeResult;
        timer.startTime = LocalDateTime.now();
        timer.isActive = true;
        timer.notified10s = false;
        timer.notified5s = false;

        emergencyTimers.put(timerKey, timer);

        log.info("[紧急计时器启动] deviceId={}, slotId={}, timeout={}秒", deviceId, slotId, EMERGENCY_TIMEOUT_SECONDS);
    }

    private void stopEmergencyTimer(String deviceId, Long slotId) {
        String timerKey = deviceId + "_" + slotId;
        EmergencyTimer timer = emergencyTimers.get(timerKey);
        if (timer != null && timer.isActive) {
            timer.isActive = false;
            log.info("[紧急计时器停止] deviceId={}, slotId={}", deviceId, slotId);
        }
    }

    @Scheduled(fixedRate = 1000)
    public void checkEmergencyTimers() {
        LocalDateTime now = LocalDateTime.now();
        
        for (Map.Entry<String, EmergencyTimer> entry : emergencyTimers.entrySet()) {
            EmergencyTimer timer = entry.getValue();
            
            if (!timer.isActive) continue;

            long secondsElapsed = java.time.Duration.between(timer.startTime, now).getSeconds();
            long secondsRemaining = EMERGENCY_TIMEOUT_SECONDS - secondsElapsed;
            
            if (secondsRemaining <= 10 && secondsRemaining > 5 && !timer.notified10s) {
                sendVoicePrompt(timer.userId, "还剩10秒，请尽快处理");
                timer.notified10s = true;
                log.info("[紧急计时器-10秒提示] deviceId={}", timer.deviceId);
            }
            
            if (secondsRemaining <= 5 && secondsRemaining > 0 && !timer.notified5s) {
                sendVoicePrompt(timer.userId, "还剩5秒，请尽快处理");
                timer.notified5s = true;
                log.info("[紧急计时器-5秒提示] deviceId={}", timer.deviceId);
            }
            
            if (secondsElapsed >= EMERGENCY_TIMEOUT_SECONDS) {
                handleEmergencyTimeout(timer);
                timer.isActive = false;
            }
        }
    }

    private void handleEmergencyTimeout(EmergencyTimer timer) {
        try {
            log.warn("[紧急超时] userId={}, deviceId={}, slotId={}", 
                timer.userId, timer.deviceId, timer.slotId);

            Map<String, Object> slot = getSlotById(timer.slotId);
            String medicineName = slot != null ? (String) slot.get("slot_name") : "未知药品";
            Integer userMedicineId = slot != null && slot.get("user_medicine_id") != null ? 
                ((Number) slot.get("user_medicine_id")).intValue() : null;

            sendEmergencyNotification(timer.userId, medicineName, timer.takeResult, timer.weightChange);

            recordBoxEvent(timer.deviceId, timer.userId, "emergency_timeout", 
                timer.slotId != null ? timer.slotId.intValue() : null, timer.weightBefore, timer.weightAfter, timer.weightChange);

            if (userMedicineId != null) {
                BigDecimal dosagePerTime = getDosagePerTime(userMedicineId);
                int expectedDosage = dosagePerTime != null ? dosagePerTime.intValue() : 1;
                int actualTake = slot != null && slot.get("actual_take_count") != null ? 
                    ((Number) slot.get("actual_take_count")).intValue() : 0;
                
                String status = "overdose".equals(timer.takeResult) ? "taken_overdose" : 
                    "underdose".equals(timer.takeResult) ? "taken_underdose" : "taken_abnormal";
                
                jdbcTemplate.update(
                    "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, taken_time, dosage_taken, " +
                    "weight_before, weight_after, weight_diff, expected_dosage, take_result, status, source, create_time) " +
                    "VALUES (?, ?, NOW(), NOW(), ?, ?, ?, ?, ?, ?, 'device', NOW())",
                    userMedicineId, timer.slotId, actualTake, timer.weightBefore, timer.weightAfter, 
                    timer.weightChange, expectedDosage, timer.takeResult, status
                );
                
                if ("overdose".equals(timer.takeResult)) {
                    int overdoseAmount = actualTake - expectedDosage;
                    jdbcTemplate.update(
                        "UPDATE user_medicines SET current_stock = GREATEST(0, current_stock - ?), update_time = NOW() WHERE id = ?",
                        overdoseAmount, userMedicineId
                    );
                } else if ("underdose".equals(timer.takeResult)) {
                    jdbcTemplate.update(
                        "UPDATE user_medicines SET current_stock = GREATEST(0, current_stock - ?), update_time = NOW() WHERE id = ?",
                        actualTake, userMedicineId
                    );
                }
                
                jdbcTemplate.update(
                    "UPDATE medicine_box_slot SET actual_take_count = 0, weight_before_take = NULL, " +
                    "weight_after_take = NULL, update_time = NOW() WHERE id = ?",
                    timer.slotId
                );
                
                log.info("[紧急超时-记录服药日志] userId={}, medicineId={}, takeResult={}, actualTake={}", 
                    timer.userId, userMedicineId, timer.takeResult, actualTake);
                
                checkMedicineDepleted(timer.userId, userMedicineId);
            }

        } catch (Exception e) {
            log.error("[紧急超时处理失败]", e);
        }
    }

    private void sendVoicePrompt(Long userId, String message) {
        try {
            log.info("[语音提示] userId={}, message={}", userId, message);
            
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> data = new HashMap<>();
            data.put("type", "VOICE_PROMPT");
            data.put("message", message);
            data.put("timestamp", System.currentTimeMillis());
            
            String jsonMessage = mapper.writeValueAsString(data);
            com.chat.older.xiechen.controller.WebSocketController.sendMessageToUser(userId, jsonMessage);
            
            log.info("[语音提示已发送WebSocket] userId={}, message={}", userId, message);
        } catch (Exception e) {
            log.error("[发送语音提示失败]", e);
        }
    }

    private void sendEmergencyNotification(Long userId, String medicineName, String takeResult, BigDecimal weightChange) {
        try {
            String elderName = getElderName(userId);
            String content;
            
            if ("overdose".equals(takeResult)) {
                content = elderName + " 服用 " + medicineName + " 时出现多服情况（" + weightChange + "克），且未及时放回药物，请立即关注！";
            } else if ("underdose".equals(takeResult)) {
                content = elderName + " 服用 " + medicineName + " 时出现少服情况，且未及时补充服用，请立即关注！";
            } else {
                content = elderName + " 在非服药时间服用了 " + medicineName + "，且未及时放回药物，请立即关注！";
            }

            notifyFamilyMembers(userId, "emergency", medicineName, elderName, content);

            jdbcTemplate.update(
                "INSERT INTO medicine_stock_alert (user_medicine_id, alert_type, alert_level, message, is_read, is_resolved, create_time) " +
                "SELECT um.id, 'emergency', 3, ?, 0, 0, NOW() FROM medicine_box_slot s " +
                "LEFT JOIN user_medicines um ON s.user_medicine_id = um.id WHERE s.slot_name = ?",
                content, medicineName
            );

            log.info("[紧急通知已发送] userId={}, medicineName={}", userId, medicineName);
        } catch (Exception e) {
            log.error("[发送紧急通知失败]", e);
        }
    }

    private void sendWrongTimeAlert(Long userId, Integer medicineId, String medicineName) {
        try {
            String elderName = getElderName(userId);
            String content = elderName + " 在非服药时间服用了 " + medicineName + "，请注意关注！";
            notifyFamilyMembers(userId, "wrong_time", medicineName, elderName, content);
        } catch (Exception e) {
            log.error("[发送非服药时间提醒失败]", e);
        }
    }

    private void sendOverdoseAlert(Long userId, Integer medicineId, String medicineName, 
                                   BigDecimal actualTaken, BigDecimal expected) {
        try {
            String elderName = getElderName(userId);
            String content = elderName + " 多服了 " + medicineName + 
                "（实际: " + actualTaken + "克, 预期: " + expected + "克），请注意关注！";
            notifyFamilyMembers(userId, "overdose", medicineName, elderName, content);
        } catch (Exception e) {
            log.error("[发送多服警告失败]", e);
        }
    }

    private void sendUnderdoseAlert(Long userId, Integer medicineId, String medicineName,
                                    BigDecimal actualTaken, BigDecimal expected) {
        try {
            String elderName = getElderName(userId);
            String content = elderName + " 少服了 " + medicineName + 
                "（实际: " + actualTaken + "克, 预期: " + expected + "克），请注意关注！";
            notifyFamilyMembers(userId, "underdose", medicineName, elderName, content);
        } catch (Exception e) {
            log.error("[发送少服提醒失败]", e);
        }
    }

    private void notifyFamilyMembers(Long elderId, String alertType, String medicineName, String elderName, String content) {
        try {
            List<Map<String, Object>> familyMembers = jdbcTemplate.queryForList(
                "SELECT user_id FROM friendships WHERE friend_id = ? AND status = 1",
                elderId
            );
            
            for (Map<String, Object> family : familyMembers) {
                Long familyUserId = ((Number) family.get("user_id")).longValue();
                
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "MEDICATION_ALERT");
                notification.put("elderId", elderId);
                notification.put("elderName", elderName);
                notification.put("alertType", alertType);
                notification.put("medicineName", medicineName);
                notification.put("content", content);
                notification.put("timestamp", System.currentTimeMillis());
                
                jdbcTemplate.update(
                    "INSERT INTO notifications (user_id, type, title, content, data, is_read, create_time) " +
                    "VALUES (?, 'MEDICATION_ALERT', ?, ?, ?, 0, NOW())",
                    familyUserId, "服药异常提醒", content, objectMapper.writeValueAsString(notification)
                );
            }
            
            log.info("[通知] 已通知 {} 位家属关于 {} 的{}情况", familyMembers.size(), elderName, alertType);
        } catch (Exception e) {
            log.error("通知家属失败", e);
        }
    }

    private boolean isInMedicationWindow(Integer userMedicineId) {
        try {
            MedicationWindowInfo windowInfo = getMedicationWindowInfoInternal(userMedicineId);
            if (windowInfo == null) {
                log.info("[时间窗口] userMedicineId={}, 无法获取窗口信息，默认允许", userMedicineId);
                return true;
            }
            
            LocalTime now = LocalTime.now();
            
            log.info("[时间窗口] userMedicineId={}, 当前时间={}, 计划时间={}, 提前允许={}, 补服截止={}", 
                userMedicineId, now, windowInfo.plannedTime, windowInfo.earlyAllowTime, windowInfo.makeupDeadTime);
            
            if (now.isBefore(windowInfo.earlyAllowTime)) {
                log.info("[时间窗口] 当前时间 < 提前允许时间，不在窗口内");
                return false;
            }
            
            if (now.isAfter(windowInfo.makeupDeadTime)) {
                log.info("[时间窗口] 当前时间 > 补服截止时间，不在窗口内");
                return false;
            }
            
            log.info("[时间窗口] 在服药时间窗口内");
            return true;
        } catch (Exception e) {
            log.error("判断服药时间窗口失败", e);
            return true;
        }
    }
    
    private boolean isInMedicationWindowBackup(Integer userMedicineId) {
        try {
            MedicationWindowInfo windowInfo = getMedicationWindowInfoInternal(userMedicineId);
            if (windowInfo == null) return true;
            
            LocalTime now = LocalTime.now();
            
            if (now.isBefore(windowInfo.earlyAllowTime)) {
                return false;
            }
            
            if (now.isAfter(windowInfo.makeupDeadTime)) {
                return false;
            }
            
            return true;
        } catch (Exception e) {
            log.error("判断服药时间窗口失败", e);
            return true;
        }
    }
    
    private MedicationWindowInfo getMedicationWindowInfo(Integer userMedicineId) {
        return getMedicationWindowInfoInternal(userMedicineId);
    }
    
    public Result getMedicationWindowInfoForApi(Integer userMedicineId) {
        try {
            MedicationWindowInfo info = getMedicationWindowInfoInternal(userMedicineId);
            if (info == null) {
                return Result.error("无法获取时间窗口信息");
            }
            
            LocalTime now = LocalTime.now();
            boolean isAfterEarly = !now.isBefore(info.earlyAllowTime);
            boolean isBeforeDeadline = !now.isAfter(info.makeupDeadTime);
            boolean inWindow = isAfterEarly && isBeforeDeadline;
            
            log.info("[时间窗口API-详细] userMedicineId={}, 当前时间={}, 提前允许={}, 补服截止={}, isAfterEarly={}, isBeforeDeadline={}, inWindow={}", 
                userMedicineId, now, info.earlyAllowTime, info.makeupDeadTime, isAfterEarly, isBeforeDeadline, inWindow);
            
            Map<String, Object> result = new HashMap<>();
            result.put("plannedTime", info.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            result.put("earlyAllowTime", info.earlyAllowTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            result.put("makeupDeadTime", info.makeupDeadTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            result.put("dosingIntervalMinutes", info.dosingIntervalMinutes);
            result.put("isFirstDose", info.isFirstDose);
            result.put("isLastDose", info.isLastDose);
            result.put("inMedicationWindow", inWindow);
            result.put("currentTime", now.format(DateTimeFormatter.ofPattern("HH:mm")));
            
            if (now.isBefore(info.earlyAllowTime)) {
                result.put("status", "too_early");
                result.put("message", "距离计划服药时间还太早");
            } else if (now.isAfter(info.makeupDeadTime)) {
                result.put("status", "missed");
                result.put("message", "已错过补服时间");
            } else if (now.isBefore(info.plannedTime)) {
                result.put("status", "early");
                result.put("message", "当前在提前服药窗口内");
            } else if (now.isAfter(info.plannedTime.plusMinutes(30))) {
                result.put("status", "makeup");
                result.put("message", "当前在补服窗口内");
            } else {
                result.put("status", "normal");
                result.put("message", "当前在正常服药窗口内");
            }
            
            log.info("[时间窗口API] userMedicineId={}, plannedTime={}, earlyAllowTime={}, makeupDeadTime={}, status={}, inMedicationWindow={}", 
                userMedicineId, result.get("plannedTime"), result.get("earlyAllowTime"), result.get("makeupDeadTime"), result.get("status"), result.get("inMedicationWindow"));
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取服药时间窗口信息失败", e);
            return Result.error("获取时间窗口信息失败: " + e.getMessage());
        }
    }
    
    private MedicationWindowInfo getMedicationWindowInfoInternal(Integer userMedicineId) {
        try {
            String reminderTimesJson = jdbcTemplate.queryForObject(
                "SELECT reminder_times FROM user_medicines WHERE id = ?", String.class, userMedicineId
            );

            if (reminderTimesJson == null) return null;

            List<String> times = parseReminderTimes(reminderTimesJson);
            LocalTime now = LocalTime.now();
            
            // 使用 MedicineBoxService 中已经修复好的逻辑
            MedicineBoxService.PlannedDoseInfo doseInfo = medicineBoxService.getPlannedDoseInfo(times, now);
            
            MedicationWindowInfo info = new MedicationWindowInfo();
            info.plannedTime = doseInfo.plannedTime;
            info.earlyAllowTime = doseInfo.earlyAllowTime;
            info.makeupDeadTime = doseInfo.makeupDeadTime;
            info.dosingIntervalMinutes = doseInfo.dosingIntervalMinutes;
            info.isFirstDose = doseInfo.isFirstDose;
            info.isLastDose = doseInfo.isLastDose;
            
            log.info("[时间窗口选择-使用统一逻辑] userMedicineId={}, 当前时间={}, 选择计划时间={}, 提前允许={}, 补服截止={}", 
                userMedicineId, now, info.plannedTime, info.earlyAllowTime, info.makeupDeadTime);
            
            return info;
        } catch (Exception e) {
            log.error("获取服药时间窗口信息失败", e);
            return null;
        }
    }
    
    private int calculateDosingIntervalMinutes(List<String> reminderTimes) {
        if (reminderTimes == null || reminderTimes.size() < 2) {
            return 8 * 60;
        }
        
        List<LocalTime> times = new ArrayList<>();
        for (String timeStr : reminderTimes) {
            try {
                times.add(LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")));
            } catch (Exception e) {
            }
        }
        Collections.sort(times);
        
        int minInterval = Integer.MAX_VALUE;
        for (int i = 1; i < times.size(); i++) {
            int interval = (int) java.time.Duration.between(times.get(i-1), times.get(i)).toMinutes();
            if (interval > 0 && interval < minInterval) {
                minInterval = interval;
            }
        }
        
        return minInterval == Integer.MAX_VALUE ? 8 * 60 : minInterval;
    }
    
    private LocalTime calculateEarlyAllowTime(LocalTime plannedTime, int dosingInterval) {
        int earlyMinutes = dosingInterval / 4;
        return plannedTime.minusMinutes(earlyMinutes);
    }
    
    private LocalTime calculateMakeupDeadTime(LocalTime plannedTime, int dosingInterval) {
        int makeupMinutes = Math.max(dosingInterval / 2, 2 * 60);
        return plannedTime.plusMinutes(makeupMinutes);
    }
    
    private List<String> parseReminderTimes(String reminderTimesJson) {
        List<String> times = new ArrayList<>();
        if (reminderTimesJson == null || reminderTimesJson.isEmpty()) {
            return times;
        }
        
        try {
            if (reminderTimesJson.startsWith("[")) {
                JSONArray arr = new JSONArray(reminderTimesJson);
                for (int i = 0; i < arr.length(); i++) {
                    times.add(arr.getString(i));
                }
            } else {
                for (String t : reminderTimesJson.split(",")) {
                    times.add(t.trim());
                }
            }
        } catch (Exception e) {
            log.error("解析提醒时间失败: {}", reminderTimesJson, e);
        }
        
        return times;
    }
    
    private static class MedicationWindowInfo {
        LocalTime plannedTime;
        LocalTime earlyAllowTime;
        LocalTime makeupDeadTime;
        int dosingIntervalMinutes;
        boolean isFirstDose;
        boolean isLastDose;
    }

    private String calculateTakeResult(BigDecimal actualTaken, BigDecimal expectedDosage) {
        BigDecimal tolerance = expectedDosage.multiply(new BigDecimal("0.15"));
        BigDecimal minThreshold = expectedDosage.subtract(tolerance);
        BigDecimal maxThreshold = expectedDosage.add(tolerance);

        if (actualTaken.compareTo(minThreshold) < 0) {
            return "underdose";
        } else if (actualTaken.compareTo(maxThreshold) > 0) {
            return "overdose";
        }
        return "normal";
    }

    private Long getUserIdByDeviceId(String deviceId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT user_id FROM medicine_box WHERE device_id = ?", Long.class, deviceId
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Long getBoxIdByDeviceId(String deviceId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT id FROM medicine_box WHERE device_id = ?", Long.class, deviceId
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> getBoxById(Long boxId) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT * FROM medicine_box WHERE id = ?", boxId
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> getSlotByBoxAndNumber(Long boxId, Integer slotNumber) {
        try {
            return jdbcTemplate.queryForMap(
                "SELECT s.*, um.custom_name FROM medicine_box_slot s " +
                "LEFT JOIN user_medicines um ON s.user_medicine_id = um.id " +
                "WHERE s.box_id = ? AND s.slot_number = ?",
                boxId, slotNumber
            );
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> getSlotById(Long slotId) {
        try {
            return jdbcTemplate.queryForMap("SELECT * FROM medicine_box_slot WHERE id = ?", slotId);
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal getFullBottleWeight(Integer userMedicineId) {
        if (userMedicineId == null) return null;
        try {
            return jdbcTemplate.queryForObject(
                "SELECT full_bottle_weight FROM user_medicines WHERE id = ?", BigDecimal.class, userMedicineId
            );
        } catch (Exception e) {
            return null;
        }
    }

    private BigDecimal getPillWeight(Integer userMedicineId) {
        if (userMedicineId == null) return null;
        try {
            BigDecimal fullWeight = jdbcTemplate.queryForObject(
                "SELECT full_bottle_weight FROM user_medicines WHERE id = ?", BigDecimal.class, userMedicineId
            );
            Integer pillsPerBottle = jdbcTemplate.queryForObject(
                "SELECT pills_per_bottle FROM user_medicines WHERE id = ?", Integer.class, userMedicineId
            );
            if (fullWeight != null && pillsPerBottle != null && pillsPerBottle > 0) {
                return fullWeight.divide(new BigDecimal(pillsPerBottle), 4, RoundingMode.HALF_UP);
            }
        } catch (Exception e) {
        }
        return null;
    }

    private String getElderName(Long userId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT nickname FROM users WHERE id = ?", String.class, userId
            );
        } catch (Exception e) {
            return "用户";
        }
    }
    
    private String getDeviceIdBySlotId(Long slotId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT b.device_id FROM medicine_box_slot s " +
                "JOIN medicine_box b ON s.box_id = b.id WHERE s.id = ?", 
                String.class, slotId
            );
        } catch (Exception e) {
            return null;
        }
    }

    private void updateSlotAfterTake(Long slotId, BigDecimal weightBefore, BigDecimal weightAfter, String takeResult) {
        String status = "normal".equals(takeResult) ? "taken" : "abnormal";
        jdbcTemplate.update(
            "UPDATE medicine_box_slot SET current_weight = ?, weight_before_take = ?, weight_after_take = ?, " +
            "take_status = ?, last_take_time = NOW(), update_time = NOW() WHERE id = ?",
            weightAfter, weightBefore, weightAfter, status, slotId
        );
    }

    private void updateMedicineStock(Integer medicineId, int takenAmount) {
        jdbcTemplate.update(
            "UPDATE user_medicines SET current_stock = GREATEST(0, current_stock - ?), update_time = NOW() WHERE id = ?",
            takenAmount, medicineId
        );
    }

    private void recordMedicationLog(Integer userMedicineId, Long slotId, BigDecimal weightBefore,
                                     BigDecimal weightAfter, BigDecimal weightDiff, BigDecimal expectedDosage,
                                     String takeResult, String source) {
        try {
            jdbcTemplate.update(
                "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, taken_time, weight_before, weight_after, " +
                "weight_diff, expected_dosage, take_result, status, source) VALUES (?, ?, NOW(), NOW(), ?, ?, ?, ?, ?, 'taken', ?)",
                userMedicineId, slotId, weightBefore, weightAfter, weightDiff, expectedDosage, takeResult, source
            );
        } catch (Exception e) {
            log.error("记录服药日志失败", e);
        }
    }

    private void recordBoxEvent(String deviceId, Long userId, String eventType, Integer slotId,
                                BigDecimal weightBefore, BigDecimal weightAfter, BigDecimal weightChange) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("slotId", slotId);
            payload.put("weightBefore", weightBefore);
            payload.put("weightAfter", weightAfter);
            payload.put("weightChange", weightChange);
            payload.put("timestamp", System.currentTimeMillis());

            jdbcTemplate.update(
                "INSERT INTO box_events (device_id, user_id, event_type, event_time, payload) VALUES (?, ?, ?, NOW(), ?)",
                deviceId, userId, eventType, objectMapper.writeValueAsString(payload)
            );
        } catch (Exception e) {
            log.error("记录药盒事件失败", e);
        }
    }

    private void initBoxSlots(Long boxId) {
        jdbcTemplate.update("DELETE FROM medicine_box_slot WHERE box_id = ?", boxId);
        for (int i = 1; i <= 6; i++) {
            jdbcTemplate.update(
                "INSERT INTO medicine_box_slot (box_id, slot_number, slot_name, current_count, max_count, current_weight, dosage_weight, take_status, status) " +
                "VALUES (?, ?, NULL, 0, 30, 0.00, NULL, 'pending', 1)",
                boxId, i
            );
        }
    }

    private void updateDeviceOnlineStatus(String deviceId, boolean online) {
        jdbcTemplate.update(
            "UPDATE medicine_box SET last_sync_time = NOW(), status = ? WHERE device_id = ?",
            online ? 1 : 0, deviceId
        );
    }

    public Result getDeviceStatus(String deviceId) {
        try {
            Map<String, Object> box = jdbcTemplate.queryForMap(
                "SELECT * FROM medicine_box WHERE device_id = ?", deviceId
            );

            DeviceState state = deviceStates.get(deviceId);

            Map<String, Object> result = new HashMap<>();
            result.put("box", box);
            result.put("state", Map.of(
                "lastWeight", state != null ? state.lastWeight : 0,
                "bottlePresent", state != null ? state.bottlePresent : false,
                "initialized", state != null ? state.initialized : false,
                "lastUpdateTime", state != null ? state.lastUpdateTime : null
            ));

            return Result.success(result);
        } catch (Exception e) {
            return Result.error("获取设备状态失败: " + e.getMessage());
        }
    }

    public Result registerDevice(Long userId, String deviceId, String boxName) {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medicine_box WHERE device_id = ?", Integer.class, deviceId
            );

            if (count != null && count > 0) {
                return Result.error("设备已被注册");
            }

            jdbcTemplate.update(
                "INSERT INTO medicine_box (user_id, device_id, box_name, status, battery_level, last_sync_time, create_time) " +
                "VALUES (?, ?, ?, 1, 100, NOW(), NOW())",
                userId, deviceId, boxName != null ? boxName : "智能药盒"
            );

            Long boxId = getBoxIdByDeviceId(deviceId);
            initBoxSlots(boxId);

            DeviceState state = new DeviceState();
            deviceStates.put(deviceId, state);

            log.info("[设备注册成功] userId={}, deviceId={}", userId, deviceId);
            return Result.success("设备注册成功");
        } catch (Exception e) {
            log.error("[设备注册失败]", e);
            return Result.error("注册失败: " + e.getMessage());
        }
    }

    private static class DeviceState {
        BigDecimal lastWeight = BigDecimal.ZERO;
        LocalDateTime lastUpdateTime;
        boolean bottlePresent = true;
        LocalDateTime bottleRemovedTime;
        boolean initialized = false;
        BigDecimal fullBottleWeight;
        BigDecimal singleDoseWeight;
        BigDecimal toleranceMin;
        BigDecimal toleranceMax;
        BigDecimal secondWeighingWeight;
        BigDecimal initFirstWeight;
        Integer pillsPerBottle;
        String initStep;
        Long initUserId;
    }

    private static class EmergencyTimer {
        Long userId;
        String deviceId;
        Long slotId;
        BigDecimal weightBefore;
        BigDecimal weightAfter;
        BigDecimal weightChange;
        String takeResult;
        LocalDateTime startTime;
        boolean isActive;
        boolean notified10s;
        boolean notified5s;
    }

    private static class MedicationConfirmationTimer {
        Long userId;
        String deviceId;
        Long slotId;
        Integer userMedicineId;
        BigDecimal actualTakenWeight;
        BigDecimal expectedDosageWeight;
        Integer expectedDosagePills;
        LocalDateTime startTime;
        boolean isActive;
        boolean weightChangeDetected;
        boolean pillReductionConfirmed;
        boolean takeButtonClicked;
    }

    public Result calculateRemainingPillsByWeight(String deviceId, Integer slotId, BigDecimal currentWeight) {
        return calculateRemainingPillsByWeight(deviceId, slotId, currentWeight, false);
    }
    
    public Result calculateRemainingPillsByWeight(String deviceId, Integer slotId, BigDecimal currentWeight, boolean forceUpdate) {
        try {
            Long boxId = getBoxIdByDeviceId(deviceId);
            if (boxId == null) {
                return Result.error("药盒不存在");
            }

            Map<String, Object> box = jdbcTemplate.queryForMap(
                "SELECT single_dose_weight, full_bottle_weight, tolerance_min, tolerance_max FROM medicine_box WHERE id = ?",
                boxId
            );

            BigDecimal singleDoseWeight = box.get("single_dose_weight") != null ?
                new BigDecimal(box.get("single_dose_weight").toString()) : null;

            if (singleDoseWeight == null || singleDoseWeight.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("[剩余粒数计算] 药盒未初始化或单粒重量未设置, deviceId={}", deviceId);
                return Result.error("药盒未初始化或单粒重量未设置");
            }

            BigDecimal emptyWeight = box.get("full_bottle_weight") != null ?
                new BigDecimal(box.get("full_bottle_weight").toString()).multiply(new BigDecimal("0.1")) :
                new BigDecimal("5.0");

            BigDecimal actualMedicineWeight = currentWeight.subtract(emptyWeight).max(BigDecimal.ZERO);

            BigDecimal maxDoseWeight = box.get("tolerance_max") != null ?
                new BigDecimal(box.get("tolerance_max").toString()) :
                singleDoseWeight.multiply(new BigDecimal("1.15"));

            Map<String, Object> slot = getSlotByBoxAndNumber(boxId, slotId);
            Integer pillsPerDose = 1;
            Integer frequencyPerDay = 1;
            Integer userMedicineId = null;
            BigDecimal lastCalculatedWeight = null;

            if (slot != null && slot.get("user_medicine_id") != null) {
                userMedicineId = ((Number) slot.get("user_medicine_id")).intValue();
                
                Map<String, Object> medicine = jdbcTemplate.queryForMap(
                    "SELECT dosage_per_time, frequency_per_day FROM user_medicines WHERE id = ?",
                    userMedicineId
                );
                
                pillsPerDose = medicine.get("dosage_per_time") != null ?
                    new BigDecimal(medicine.get("dosage_per_time").toString()).intValue() : 1;
                
                frequencyPerDay = medicine.get("frequency_per_day") != null ?
                    ((Number) medicine.get("frequency_per_day")).intValue() : 1;
                
                lastCalculatedWeight = slot.get("last_calculated_weight") != null ?
                    new BigDecimal(slot.get("last_calculated_weight").toString()) : null;
            }

            BigDecimal weightChangeThreshold = new BigDecimal("0.1");
            boolean shouldUpdate = true;
            if (!forceUpdate && lastCalculatedWeight != null) {
                BigDecimal weightDiff = currentWeight.subtract(lastCalculatedWeight).abs();
                if (weightDiff.compareTo(weightChangeThreshold) < 0) {
                    log.info("[剩余粒数计算] 重量变化{}g小于0.1g阈值，跳过更新, deviceId={}, slotId={}", 
                        weightDiff, deviceId, slotId);
                    shouldUpdate = false;
                }
            }
            
            if (forceUpdate) {
                log.info("[剩余粒数计算] 强制更新模式，忽略重量变化阈值检查, deviceId={}, slotId={}", deviceId, slotId);
                shouldUpdate = true;
            }

            int availableDoses = actualMedicineWeight.divide(maxDoseWeight, 0, RoundingMode.DOWN).intValue();
            availableDoses = Math.max(0, availableDoses);

            int remainingPills = availableDoses * pillsPerDose;

            int availableDays = frequencyPerDay > 0 ? availableDoses / frequencyPerDay : 0;

            if (userMedicineId != null && shouldUpdate) {
                jdbcTemplate.update(
                    "UPDATE user_medicines SET current_stock = ?, update_time = NOW() WHERE id = ?",
                    remainingPills, userMedicineId
                );
                
                jdbcTemplate.update(
                    "UPDATE medicine_box_slot SET current_weight = ?, remaining_pills = ?, available_doses = ?, " +
                    "available_days = ?, last_calculated_weight = ?, update_time = NOW() WHERE id = ?",
                    currentWeight, remainingPills, availableDoses, availableDays, currentWeight,
                    ((Number) slot.get("id")).longValue()
                );
                
                log.info("[剩余粒数计算] deviceId={}, slotId={}, currentWeight={}g, emptyWeight={}g, actualMedicineWeight={}g, " +
                        "singleDoseWeight={}g, maxDoseWeight={}g, pillsPerDose={}粒, " +
                        "availableDoses={}次, remainingPills={}粒, frequencyPerDay={}次/天, availableDays={}天",
                    deviceId, slotId, currentWeight, emptyWeight, actualMedicineWeight,
                    singleDoseWeight, maxDoseWeight, pillsPerDose,
                    availableDoses, remainingPills, frequencyPerDay, availableDays);
            } else if (userMedicineId != null) {
                log.info("[剩余粒数计算] 重量变化不足0.1g，仅返回计算结果不更新数据库, deviceId={}, slotId={}",
                    deviceId, slotId);
            } else {
                log.warn("[剩余粒数计算] 未找到关联药品, deviceId={}, slotId={}", deviceId, slotId);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("remainingPills", remainingPills);
            result.put("availableDoses", availableDoses);
            result.put("availableDays", availableDays);
            result.put("currentWeight", currentWeight);
            result.put("emptyWeight", emptyWeight);
            result.put("actualMedicineWeight", actualMedicineWeight);
            result.put("singleDoseWeight", singleDoseWeight);
            result.put("maxDoseWeight", maxDoseWeight);
            result.put("pillsPerDose", pillsPerDose);
            result.put("frequencyPerDay", frequencyPerDay);

            return Result.success(result);
        } catch (Exception e) {
            log.error("[剩余粒数计算失败]", e);
            return Result.error("计算失败: " + e.getMessage());
        }
    }

    public Result startMedicationConfirmation(Long userId, String deviceId, Long slotId,
                                                Integer userMedicineId, BigDecimal actualTakenWeight,
                                                BigDecimal expectedDosageWeight, Integer expectedDosagePills) {
        String timerKey = deviceId + "_" + slotId + "_confirm";

        MedicationConfirmationTimer existingTimer = medicationConfirmationTimers.get(timerKey);
        if (existingTimer != null && existingTimer.isActive) {
            return Result.error("已有确认流程在进行中");
        }

        MedicationConfirmationTimer timer = new MedicationConfirmationTimer();
        timer.userId = userId;
        timer.deviceId = deviceId;
        timer.slotId = slotId;
        timer.userMedicineId = userMedicineId;
        timer.actualTakenWeight = actualTakenWeight;
        timer.expectedDosageWeight = expectedDosageWeight;
        timer.expectedDosagePills = expectedDosagePills;
        timer.startTime = LocalDateTime.now();
        timer.isActive = true;
        timer.weightChangeDetected = false;
        timer.pillReductionConfirmed = false;
        timer.takeButtonClicked = false;

        medicationConfirmationTimers.put(timerKey, timer);

        sendVoicePrompt(userId, "请确认您已完成服药操作，并点击服药确认按钮");

        log.info("[服药确认启动] userId={}, deviceId={}, slotId={}", userId, deviceId, slotId);
        return Result.success("服药确认已启动，请在60秒内完成操作");
    }

    public Result confirmPillReduction(String deviceId, Long slotId) {
        String timerKey = deviceId + "_" + slotId + "_confirm";
        MedicationConfirmationTimer timer = medicationConfirmationTimers.get(timerKey);

        if (timer == null || !timer.isActive) {
            return Result.error("没有进行中的服药确认流程");
        }

        timer.pillReductionConfirmed = true;
        log.info("[粒数减少确认] deviceId={}, slotId={}", deviceId, slotId);

        checkAndCompleteMedicationConfirmation(timerKey, timer);
        return Result.success("粒数减少已确认");
    }

    public Result clickTakeConfirmationButton(String deviceId, Long slotId) {
        String timerKey = deviceId + "_" + slotId + "_confirm";
        MedicationConfirmationTimer timer = medicationConfirmationTimers.get(timerKey);

        if (timer == null || !timer.isActive) {
            return Result.error("没有进行中的服药确认流程");
        }

        timer.takeButtonClicked = true;
        log.info("[确认按钮点击] deviceId={}, slotId={}", deviceId, slotId);

        checkAndCompleteMedicationConfirmation(timerKey, timer);
        return Result.success("确认按钮已点击");
    }

    private void checkAndCompleteMedicationConfirmation(String timerKey, MedicationConfirmationTimer timer) {
        if (timer.pillReductionConfirmed && timer.takeButtonClicked) {
            timer.isActive = false;
            sendVoicePrompt(timer.userId, "服药确认完成，已记录您的服药信息");
            log.info("[服药确认完成] userId={}, deviceId={}", timer.userId, timer.deviceId);
        }
    }

    @Scheduled(fixedRate = 1000)
    public void checkMedicationConfirmationTimers() {
        LocalDateTime now = LocalDateTime.now();

        for (Map.Entry<String, MedicationConfirmationTimer> entry : medicationConfirmationTimers.entrySet()) {
            MedicationConfirmationTimer timer = entry.getValue();

            if (!timer.isActive) continue;

            long secondsElapsed = java.time.Duration.between(timer.startTime, now).getSeconds();

            if (secondsElapsed >= MEDICATION_CONFIRMATION_TIMEOUT_SECONDS) {
                handleMedicationConfirmationTimeout(entry.getKey(), timer);
                timer.isActive = false;
            }
        }
    }

    private void handleMedicationConfirmationTimeout(String timerKey, MedicationConfirmationTimer timer) {
        try {
            log.warn("[服药确认超时] userId={}, deviceId={}, slotId={}",
                timer.userId, timer.deviceId, timer.slotId);

            if (timer.userMedicineId != null) {
                jdbcTemplate.update(
                    "UPDATE user_medicines SET current_stock = GREATEST(0, current_stock - ?), update_time = NOW() WHERE id = ?",
                    timer.expectedDosagePills, timer.userMedicineId
                );

                jdbcTemplate.update(
                    "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, taken_time, dosage_taken, " +
                    "expected_dosage, take_result, status, source, create_time) VALUES (?, ?, NOW(), NOW(), ?, ?, 'auto_confirmed', 'taken', 'device', NOW())",
                    timer.userMedicineId, timer.slotId, timer.expectedDosagePills, timer.expectedDosagePills
                );

                checkMedicineDepleted(timer.userId, timer.userMedicineId);
            }

            sendVoicePrompt(timer.userId, "服药超时，系统已自动记录您的服药信息");
            log.info("[服药确认超时-自动处理] userId={}, medicineId={}, dosage={}",
                timer.userId, timer.userMedicineId, timer.expectedDosagePills);

        } catch (Exception e) {
            log.error("[服药确认超时处理失败]", e);
        }
    }

    public Result verifyMedicationDosageByWeight(BigDecimal weightBefore, BigDecimal weightAfter,
                                                  BigDecimal expectedDosageWeight, BigDecimal toleranceMin,
                                                  BigDecimal toleranceMax) {
        try {
            BigDecimal actualTakenWeight = weightBefore.subtract(weightAfter).max(BigDecimal.ZERO);

            String takeResult;
            String status;
            String message;

            if (actualTakenWeight.compareTo(toleranceMin) < 0) {
                takeResult = "underdose";
                status = "need_more";
                message = String.format("服药量不足，实际取出 %.2fg，预期为 %.2fg ±15%% (%.2fg ~ %.2fg)，请继续取药",
                    actualTakenWeight, expectedDosageWeight, toleranceMin, toleranceMax);
            } else if (actualTakenWeight.compareTo(toleranceMax) > 0) {
                takeResult = "overdose";
                status = "need_restock";
                message = String.format("服药量过多，实际取出 %.2fg，预期为 %.2fg ±15%% (%.2fg ~ %.2fg)，请放回多余药品",
                    actualTakenWeight, expectedDosageWeight, toleranceMin, toleranceMax);
            } else {
                takeResult = "normal";
                status = "success";
                message = String.format("服药剂量正确，实际取出 %.2fg，在预期范围 %.2fg ~ %.2fg 内",
                    actualTakenWeight, toleranceMin, toleranceMax);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("actualTakenWeight", actualTakenWeight);
            result.put("expectedDosageWeight", expectedDosageWeight);
            result.put("toleranceMin", toleranceMin);
            result.put("toleranceMax", toleranceMax);
            result.put("takeResult", takeResult);
            result.put("status", status);
            result.put("message", message);
            result.put("isWithinTolerance", "normal".equals(takeResult));

            log.info("[剂量验证] weightBefore={}, weightAfter={}, actual={}, expected={}, result={}",
                weightBefore, weightAfter, actualTakenWeight, expectedDosageWeight, takeResult);

            return Result.success(result);
        } catch (Exception e) {
            log.error("[剂量验证失败]", e);
            return Result.error("剂量验证失败: " + e.getMessage());
        }
    }

    public Result verifyDosageWithRefill(BigDecimal initialWeightBefore, BigDecimal initialWeightAfter,
                                           BigDecimal refillWeightBefore, BigDecimal refillWeightAfter,
                                           BigDecimal expectedDosageWeight, BigDecimal toleranceMin,
                                           BigDecimal toleranceMax) {
        try {
            BigDecimal initialTaken = initialWeightBefore.subtract(initialWeightAfter).max(BigDecimal.ZERO);
            BigDecimal refillAmount = refillWeightAfter.subtract(refillWeightBefore).max(BigDecimal.ZERO);
            BigDecimal netTakenWeight = initialTaken.subtract(refillAmount).max(BigDecimal.ZERO);

            String takeResult;
            String status;
            String message;

            if (netTakenWeight.compareTo(toleranceMin) < 0) {
                takeResult = "underdose";
                status = "need_more";
                message = String.format("净服药量不足，初始取出 %.2fg，放回 %.2fg，净服用 %.2fg，预期为 %.2fg ±15%% (%.2fg ~ %.2fg)",
                    initialTaken, refillAmount, netTakenWeight, expectedDosageWeight, toleranceMin, toleranceMax);
            } else if (netTakenWeight.compareTo(toleranceMax) > 0) {
                takeResult = "overdose";
                status = "need_restock";
                message = String.format("净服药量过多，初始取出 %.2fg，放回 %.2fg，净服用 %.2fg，预期为 %.2fg ±15%% (%.2fg ~ %.2fg)",
                    initialTaken, refillAmount, netTakenWeight, expectedDosageWeight, toleranceMin, toleranceMax);
            } else {
                takeResult = "normal";
                status = "success";
                message = String.format("净服药剂量正确，初始取出 %.2fg，放回 %.2fg，净服用 %.2fg，在预期范围 %.2fg ~ %.2fg 内",
                    initialTaken, refillAmount, netTakenWeight, toleranceMin, toleranceMax);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("initialTakenWeight", initialTaken);
            result.put("refillAmount", refillAmount);
            result.put("netTakenWeight", netTakenWeight);
            result.put("expectedDosageWeight", expectedDosageWeight);
            result.put("toleranceMin", toleranceMin);
            result.put("toleranceMax", toleranceMax);
            result.put("takeResult", takeResult);
            result.put("status", status);
            result.put("message", message);
            result.put("isWithinTolerance", "normal".equals(takeResult));

            log.info("[净剂量验证] initialTaken={}, refill={}, net={}, expected={}, result={}",
                initialTaken, refillAmount, netTakenWeight, expectedDosageWeight, takeResult);

            return Result.success(result);
        } catch (Exception e) {
            log.error("[净剂量验证失败]", e);
            return Result.error("净剂量验证失败: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 60000)
    public void checkMedicineReminders() {
        log.info("[定时任务] 开始检查吃药提醒...");
        
        try {
            LocalTime now = LocalTime.now();
            String nowStr = String.format("%02d:%02d", now.getHour(), now.getMinute());
            
            String sql = "SELECT DISTINCT um.id, um.user_id, um.custom_name, um.reminder_times, " +
                        "b.device_id, u.nickname as elder_name " +
                        "FROM user_medicines um " +
                        "LEFT JOIN medicine_box_slot s ON um.id = s.user_medicine_id " +
                        "LEFT JOIN medicine_box b ON s.box_id = b.id " +
                        "LEFT JOIN users u ON um.user_id = u.id " +
                        "WHERE um.is_deleted = 0 " +
                        "AND um.reminder_times IS NOT NULL " +
                        "AND b.device_id IS NOT NULL";
            
            List<Map<String, Object>> medicines = jdbcTemplate.queryForList(sql);
            
            for (Map<String, Object> medicine : medicines) {
                String reminderTimesJson = (String) medicine.get("reminder_times");
                if (reminderTimesJson == null || reminderTimesJson.isEmpty()) {
                    continue;
                }
                
                try {
                    com.alibaba.fastjson2.JSONArray times = com.alibaba.fastjson2.JSON.parseArray(reminderTimesJson);
                    if (times == null || times.isEmpty()) {
                        continue;
                    }
                    
                    for (int i = 0; i < times.size(); i++) {
                        String timeStr = times.getString(i);
                        if (timeStr != null && timeStr.equals(nowStr)) {
                            String deviceId = (String) medicine.get("device_id");
                            String elderName = (String) medicine.get("elder_name");
                            String medicineName = (String) medicine.get("custom_name");
                            Long elderId = (Long) medicine.get("user_id");
                            
                            log.info("[吃药提醒触发] 用户: {}, 药品: {}, 时间: {}, 设备: {}", 
                                elderName, medicineName, nowStr, deviceId);
                            
                            sendMedicineReminder(deviceId);
                            
                            Long logId = createMedicationLog(medicine, timeStr);
                            
                            notifyFamilyMembers(elderId, "reminder", medicineName, elderName, timeStr);
                            
                            sendMedicineReminderToElder(elderId, medicineName, timeStr);
                            
                            break;
                        }
                    }
                } catch (Exception e) {
                    log.error("[解析提醒时间失败] medicineId={}", medicine.get("id"), e);
                }
            }
            
            log.info("[定时任务] 吃药提醒检查完成");
        } catch (Exception e) {
            log.error("[定时任务] 吃药提醒检查失败", e);
        }
    }

    private void sendMedicineReminderToElder(Long elderId, String medicineName, String timeStr) {
        try {
            Map<String, Object> message = new HashMap<>();
            message.put("msgType", "MEDICATION_REMINDER");
            message.put("type", "MEDICATION_REMINDER");
            message.put("medicineName", medicineName);
            message.put("time", timeStr);
            message.put("timestamp", System.currentTimeMillis());
            
            chatHandler.sendToUser(elderId, message);
            
            log.info("[用药提醒] 已推送用药提醒给长辈 {}: 药品={}, 时间={}", elderId, medicineName, timeStr);
        } catch (Exception e) {
            log.error("[用药提醒] 推送给长辈失败", e);
        }
    }

    private void sendDeviceEventToElder(Long elderId, String eventType, String message, String medicineName, String details) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("msgType", "DEVICE_EVENT");
            data.put("type", eventType);
            data.put("message", message);
            data.put("medicineName", medicineName);
            data.put("details", details);
            data.put("timestamp", System.currentTimeMillis());
            
            chatHandler.sendToUser(elderId, data);
            
            log.info("[设备事件] 已推送设备事件给长辈 {}: 事件={}, 药品={}", elderId, eventType, medicineName);
        } catch (Exception e) {
            log.error("[设备事件] 推送给长辈失败", e);
        }
    }

    private Long createMedicationLog(Map<String, Object> medicine, String timeStr) {
        try {
            Integer userMedicineId = ((Number) medicine.get("id")).intValue();
            Long userId = (Long) medicine.get("user_id");
            
            LocalDate today = LocalDate.now();
            LocalTime reminderTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
            LocalDateTime scheduledTime = LocalDateTime.of(today, reminderTime);
            
            List<Map<String, Object>> slots = jdbcTemplate.queryForList(
                "SELECT id FROM medicine_box_slot WHERE user_medicine_id = ?",
                userMedicineId
            );
            
            Long slotId = null;
            if (!slots.isEmpty()) {
                slotId = ((Number) slots.get(0).get("id")).longValue();
            }
            
            jdbcTemplate.update(
                "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, status, source, create_time) " +
                "VALUES (?, ?, ?, 'pending', 'system', NOW())",
                userMedicineId, slotId, scheduledTime
            );
            
            return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        } catch (Exception e) {
            log.error("[创建服药日志失败]", e);
            return null;
        }
    }
}

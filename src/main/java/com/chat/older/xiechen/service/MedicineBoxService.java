package com.chat.older.xiechen.service;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.MedicineBoxMapper;
import com.chat.older.xiechen.model.MedicineBox;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MedicineBoxService {

    private static final Logger log = LoggerFactory.getLogger(MedicineBoxService.class);
    private static final BigDecimal GRAM_PER_PILL = new BigDecimal("1.0");

    @Autowired
    private MedicineBoxMapper medicineBoxMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;
    
    @Autowired
    private NotificationService notificationService;
    
    @Autowired
    private MedicineBoxDeviceService deviceService;

    public Result getBoxStatus(Long userId) {
        try {
            MedicineBox box = medicineBoxMapper.selectByUserId(userId);
            if (box == null) {
                box = createDefaultBox(userId, "BOX" + userId);
            }
            
            LocalDateTime now = LocalDateTime.now();
            if (box.getIsLocked() != null && box.getIsLocked() && 
                box.getLockedUntil() != null && box.getLockedUntil().isBefore(now)) {
                box.setIsLocked(false);
                box.setLockedUntil(null);
                box.setUpdateTime(now);
                medicineBoxMapper.update(box);
                log.info("[智能药盒解锁] 用户 {} 的智能药盒已自动解锁", userId);
            }
            
            List<Map<String, Object>> slots = getBoxSlotsWithMedicineInfo(box.getId());
            List<Map<String, Object>> userMedicines = getUserMedicinesWithStock(userId);
            List<Map<String, Object>> alerts = getStockAlerts(userId);
            
            Map<String, Object> result = new HashMap<>();
            result.put("box", box);
            result.put("slots", slots);
            result.put("userMedicines", userMedicines);
            result.put("alerts", alerts);
            result.put("isLocked", box.getIsLocked());
            result.put("lockedUntil", box.getLockedUntil());
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取药盒状态失败", e);
            return Result.error("获取药盒状态失败: " + e.getMessage());
        }
    }

    @Transactional
    public Result bindMedicineToSlot(Long userId, Long slotId, Long userMedicineId) {
        List<Map<String, Object>> medicines = jdbcTemplate.queryForList(
            "SELECT id, custom_name, reminder_times, dosage_per_time, current_stock, frequency_per_day FROM user_medicines WHERE id = ? AND user_id = ? AND is_deleted = 0",
            userMedicineId, userId
        );
        
        if (medicines == null || medicines.isEmpty()) {
            return Result.error("药品不存在或无权操作");
        }
        
        Map<String, Object> medicine = medicines.get(0);
        String medicineName = (String) medicine.get("custom_name");
        BigDecimal dosagePerTime = medicine.get("dosage_per_time") != null ? 
            new BigDecimal(medicine.get("dosage_per_time").toString()) : new BigDecimal("1");
        int currentStock = medicine.get("current_stock") != null ? 
            ((Number) medicine.get("current_stock")).intValue() : 0;
        
        BigDecimal currentWeight = new BigDecimal(currentStock).multiply(GRAM_PER_PILL);
        
        int updated = jdbcTemplate.update(
            "UPDATE medicine_box_slot SET user_medicine_id = ?, slot_name = ?, " +
            "dosage_weight = ?, current_weight = ?, take_status = 'pending', update_time = NOW() WHERE id = ?",
            userMedicineId, medicineName != null ? medicineName : "药品", 
            dosagePerTime, currentWeight, slotId
        );
        
        if (updated > 0) {
            Map<String, Object> result = new HashMap<>();
            result.put("message", "绑定成功");
            result.put("medicineName", medicineName);
            result.put("currentWeight", currentWeight);
            result.put("dosagePerTime", dosagePerTime);
            return Result.success(result);
        }
        return Result.error("绑定失败");
    }

    @Transactional
    public Result simulateTakeMedicine(Long userId, String deviceId) {
        MedicineBox box = medicineBoxMapper.selectByUserId(userId);
        if (box == null) {
            box = createDefaultBox(userId, deviceId);
        }

        LocalTime now = LocalTime.now();
        String currentTimePeriod = getCurrentTimePeriod(now);
        
        Map<String, Object> targetSlot = findSlotWithMedicine(box.getId());
        
        if (targetSlot == null) {
            return Result.error("没有可用的药仓");
        }

        Integer slotId = ((Number) targetSlot.get("id")).intValue();
        return executeTakeMedicine(userId, box, slotId, currentTimePeriod, "normal");
    }

    @Transactional
    public Result simulateTakeMedicineBySlot(Long userId, Long slotId, String timePeriod, String takeType) {
        MedicineBox box = medicineBoxMapper.selectByUserId(userId);
        if (box == null) {
            box = createDefaultBox(userId, "BOX" + userId);
        }

        if (timePeriod == null || timePeriod.isEmpty()) {
            timePeriod = getCurrentTimePeriod(LocalTime.now());
        }

        return executeTakeMedicine(userId, box, slotId.intValue(), timePeriod, takeType);
    }
    
    @Transactional
    public Result takeMedicineByDevice(Long userId, String deviceId, Long medicineId, String medicineName, Integer quantity, String takeResult, String unit, Double weightBefore, Double weightAfter, Double weightDiff) {
        log.info("[真实设备服药] userId={}, deviceId={}, medicineId={}, quantity={}, weightDiff={}", userId, deviceId, medicineId, quantity, weightDiff);
        
        MedicineBox box = medicineBoxMapper.selectByUserId(userId);
        if (box == null) {
            box = createDefaultBox(userId, deviceId);
        }
        
        if (medicineId == null) {
            return Result.error("未绑定药品");
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();
        String timePeriod = getCurrentTimePeriod(currentTime);
        
        String reminderTimesJson = getReminderTimes(medicineId.intValue());
        List<String> reminderTimes = parseReminderTimes(reminderTimesJson);
        PlannedDoseInfo doseInfo = getPlannedDoseInfo(reminderTimes, currentTime);
        
        BigDecimal dosagePerTime = getDosagePerTime(medicineId.intValue());
        int expectedDosage = dosagePerTime != null ? dosagePerTime.intValue() : 1;
        
        List<Map<String, Object>> slots = jdbcTemplate.queryForList(
            "SELECT id, actual_take_count, expected_take_count, take_match_status, current_weight FROM medicine_box_slot WHERE user_medicine_id = ?",
            medicineId
        );
        
        Long slotId = null;
        int currentActualTake = 0;
        int currentExpectedTake = expectedDosage;
        BigDecimal currentWeight = BigDecimal.ZERO;
        
        if (!slots.isEmpty()) {
            Map<String, Object> slot = slots.get(0);
            slotId = ((Number) slot.get("id")).longValue();
            currentActualTake = slot.get("actual_take_count") != null ? ((Number) slot.get("actual_take_count")).intValue() : 0;
            currentExpectedTake = slot.get("expected_take_count") != null ? ((Number) slot.get("expected_take_count")).intValue() : expectedDosage;
            currentWeight = slot.get("current_weight") != null ? new BigDecimal(slot.get("current_weight").toString()) : BigDecimal.ZERO;
        }
        
        if (slotId == null) {
            return Result.error("未找到关联的药仓");
        }
        
        // 特殊处理：非服药时间取药（包括 blocked 和 missed 情况）
        if ("blocked".equals(takeResult) || "missed".equals(takeResult) || "blocked_not_returned".equals(takeResult)) {
            log.info("[非服药时间取药] userId={}, medicineId={}, quantity={}, takeResult={}", userId, medicineId, quantity, takeResult);
            
            // 更新药仓状态
            int newActualTake = currentActualTake + quantity;
            BigDecimal newWeight = currentWeight.subtract(new BigDecimal(quantity)).max(BigDecimal.ZERO);
            
            jdbcTemplate.update(
                "UPDATE medicine_box_slot SET actual_take_count = ?, current_weight = ?, update_time = NOW() WHERE id = ?",
                newActualTake, newWeight, slotId
            );
            
            // 更新药品库存 - 确保无论什么情况，只要取药就减少库存
            updateMedicineStock(medicineId.intValue(), quantity);
            
            // 计算并更新剩余粒数、可服用次数、可服用天数
            try {
                deviceService.calculateRemainingPillsByWeight(deviceId, 1, newWeight);
            } catch (Exception e) {
                log.warn("[非服药时间取药] 计算剩余粒数失败: {}", e.getMessage());
            }
            
            // 插入异常服药记录（使用 status='abnormal' 区分正常服药）
            String statusType = "blocked".equals(takeResult) ? "blocked" : 
                                "missed".equals(takeResult) ? "missed" : "blocked_not_returned";
            jdbcTemplate.update(
                "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, taken_time, dosage_taken, " +
                "weight_before, weight_after, weight_diff, expected_dosage, take_result, status, source, create_time) " +
                "VALUES (?, ?, NOW(), NOW(), ?, ?, ?, ?, ?, ?, 'abnormal', 'device', NOW())",
                medicineId, slotId, quantity, weightBefore, weightAfter, weightDiff, quantity, statusType
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("medicineName", medicineName);
            result.put("actualTaken", quantity);
            result.put("takeResult", takeResult);
            result.put("status", "abnormal");
            result.put("stockAfter", getRemainingStock(medicineId.intValue()));
            result.put("unit", unit);
            result.put("message", "已记录服药并更新库存");
            result.put("remainingPillsUpdated", true);
            
            log.info("[非服药时间取药] 已记录服药并更新库存, userId={}, medicineId={}, quantity={}", userId, medicineId, quantity);
            
            return Result.success(result);
        }
        
        int newActualTake = currentActualTake + quantity;
        BigDecimal newWeight = currentWeight.subtract(new BigDecimal(quantity)).max(BigDecimal.ZERO);
        
        jdbcTemplate.update(
            "UPDATE medicine_box_slot SET actual_take_count = ?, current_weight = ?, update_time = NOW() WHERE id = ?",
            newActualTake, newWeight, slotId
        );
        
        String cumulativeResult = judgeCumulativeTakeResult(newActualTake, expectedDosage);
        
        Map<String, Object> result = new HashMap<>();
        result.put("medicineName", medicineName);
        result.put("actualTaken", quantity);
        result.put("cumulativeTaken", newActualTake);
        result.put("expectedDosage", expectedDosage);
        result.put("cumulativeResult", cumulativeResult);
        result.put("stockAfter", getRemainingStock(medicineId.intValue()));
        result.put("unit", unit);
        result.put("timePeriod", timePeriod);
        result.put("slotId", slotId);
        result.put("userMedicineId", medicineId);
        
        if ("underdose".equals(cumulativeResult)) {
            result.put("needMore", true);
            result.put("message", "服药量不足，请继续取药");
            result.put("detailMessage", "您已取 " + newActualTake + " " + unit + "，本次应服 " + expectedDosage + " " + unit + "，请继续取药。");
            log.info("[累积服药-少服] userId={}, 已取={}, 应取={}", userId, newActualTake, expectedDosage);
            return Result.success(result);
        }
        
        if ("overdose".equals(cumulativeResult)) {
            result.put("needRestock", true);
            result.put("message", "服药量过多，请放回多余药品");
            result.put("detailMessage", "您已取 " + newActualTake + " " + unit + "，本次应服 " + expectedDosage + " " + unit + "，请放回 " + (newActualTake - expectedDosage) + " " + unit + "。");
            log.info("[累积服药-多服] userId={}, 已取={}, 应取={}", userId, newActualTake, expectedDosage);
            return Result.success(result);
        }
        
        completeMedicationSession(userId, medicineId, slotId, newActualTake, expectedDosage, weightBefore, weightAfter, weightDiff, "normal", doseInfo);
        
        result.put("takeSuccess", true);
        result.put("message", "服药成功");
        result.put("detailMessage", "您已正确服用 " + expectedDosage + " " + unit + " 药品。");
        
        if (doseInfo != null) {
            LocalTime nowTime = LocalTime.now();
            if (nowTime.isAfter(doseInfo.plannedTime.plusMinutes(30))) {
                result.put("isMakeup", true);
                result.put("message", "补服成功");
            } else if (nowTime.isBefore(doseInfo.plannedTime)) {
                result.put("isEarly", true);
                result.put("message", "提前服药成功");
            }
        }
        
        log.info("[累积服药-正常] userId={}, 已取={}, 应取={}", userId, newActualTake, expectedDosage);
        
        return Result.success(result);
    }
    
    @Transactional
    public Result takeMedicineByDeviceOptimized(Long userId, String deviceId, Long medicineId, String medicineName, 
                                                  Double weightBefore, Double weightAfter, Double weightDiff,
                                                  Boolean deviceConnected, Boolean boxLocked, Integer batteryLevel,
                                                  Double calibrationFactor, List<Double> weightMeasurements) {
        log.info("[真实设备服药优化] userId={}, deviceId={}, medicineId={}, deviceConnected={}, boxLocked={}", 
            userId, deviceId, medicineId, deviceConnected, boxLocked);
        
        MedicineBox box = medicineBoxMapper.selectByUserId(userId);
        if (box == null) {
            box = createDefaultBox(userId, deviceId);
        }
        
        Map<String, Object> validationResult = validateDeviceState(deviceConnected, boxLocked, box, batteryLevel);
        if (validationResult != null) {
            return Result.error(400, (String) validationResult.get("message"), validationResult);
        }
        
        if (medicineId == null) {
            return Result.error("未绑定药品");
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();
        String timePeriod = getCurrentTimePeriod(currentTime);
        
        String reminderTimesJson = getReminderTimes(medicineId.intValue());
        List<String> reminderTimes = parseReminderTimes(reminderTimesJson);
        PlannedDoseInfo doseInfo = getPlannedDoseInfo(reminderTimes, currentTime);
        
        log.info("[真实设备服药-详细调试] 当前时间={}, 计划时间={}, 提前允许={}, 补服截止={}", 
            currentTime, doseInfo.plannedTime, doseInfo.earlyAllowTime, doseInfo.makeupDeadTime);
        
        MedicationAction action = judgeMedicationAction(currentTime, doseInfo);
        
        log.info("[真实设备服药-详细调试] 判断结果 type={}, message={}", action.type, action.message);
        
        if ("too_early".equals(action.type) || "missed".equals(action.type)) {
            Map<String, Object> warningData = new HashMap<>();
            warningData.put("type", action.type);
            warningData.put("message", action.message);
            warningData.put("detailMessage", action.detailMessage);
            warningData.put("medicineName", medicineName);
            warningData.put("plannedTime", doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            warningData.put("currentTime", currentTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            
            if (doseInfo.earlyAllowTime != null) {
                warningData.put("earlyAllowTime", doseInfo.earlyAllowTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            if (doseInfo.makeupDeadTime != null) {
                warningData.put("makeupDeadTime", doseInfo.makeupDeadTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            }
            
            recordBoxEvent(deviceId, userId, "blocked_" + action.type, medicineId.intValue(), warningData);
            return Result.error(400, action.message, warningData);
        }
        
        Map<String, Object> duplicateCheck = checkDuplicateMedicationOptimized(medicineId, doseInfo, now);
        if (duplicateCheck != null) {
            return Result.error(400, (String) duplicateCheck.get("message"), duplicateCheck);
        }
        
        BigDecimal dosagePerTime = getDosagePerTime(medicineId.intValue());
        int expectedDosage = dosagePerTime != null ? dosagePerTime.intValue() : 1;
        
        double actualWeightDiff = calculateAverageWeightDiff(weightMeasurements, weightDiff);
        BigDecimal calibratedFactor = calibrationFactor != null ? new BigDecimal(calibrationFactor) : BigDecimal.ONE;
        BigDecimal actualTakenGrams = new BigDecimal(actualWeightDiff).max(BigDecimal.ZERO);

        List<Map<String, Object>> slots = jdbcTemplate.queryForList(
            "SELECT id, actual_take_count, expected_take_count, take_match_status, current_weight, dosage_weight FROM medicine_box_slot WHERE user_medicine_id = ?",
            medicineId
        );

        Long slotId = null;
        BigDecimal currentWeight = BigDecimal.ZERO;
        BigDecimal singleDoseWeight = null;

        if (!slots.isEmpty()) {
            Map<String, Object> slot = slots.get(0);
            slotId = ((Number) slot.get("id")).longValue();
            currentWeight = slot.get("current_weight") != null ? new BigDecimal(slot.get("current_weight").toString()) : BigDecimal.ZERO;
            if (slot.get("dosage_weight") != null) {
                singleDoseWeight = new BigDecimal(slot.get("dosage_weight").toString());
            }
        }

        if (slotId == null) {
            return Result.error("未找到关联的药仓");
        }

        BigDecimal actualTaken;
        if (singleDoseWeight != null && singleDoseWeight.compareTo(BigDecimal.ZERO) > 0) {
            actualTaken = actualTakenGrams.divide(singleDoseWeight, 4, RoundingMode.HALF_UP)
                .multiply(dosagePerTime);
            log.info("[真实设备服药-克数转粒数] weightDiff={}g, singleDoseWeight={}g, dosagePerTime={}粒, 转换后actualTaken={}粒",
                actualWeightDiff, singleDoseWeight, dosagePerTime, actualTaken);
        } else {
            actualTaken = actualTakenGrams;
            log.warn("[真实设备服药] 未获取到单次服药重量，直接使用克数比较");
        }

        String takeResult = calculateTakeResult(actualTaken, dosagePerTime, calibratedFactor);

        long timeDeviationSeconds = calculateTimeDeviation(currentTime, doseInfo.plannedTime);

        BigDecimal newWeight = currentWeight.subtract(actualTakenGrams).max(BigDecimal.ZERO);
        
        jdbcTemplate.update(
            "UPDATE medicine_box_slot SET current_weight = ?, weight_before_take = ?, weight_after_take = ?, last_take_time = NOW(), update_time = NOW() WHERE id = ?",
            newWeight, weightBefore, weightAfter, slotId
        );
        
        updateMedicineStock(medicineId.intValue(), actualTaken.setScale(0, RoundingMode.HALF_UP).intValue());
        
        String status = "taken";
        if ("early".equals(action.type)) {
            status = "taken_early";
        } else if ("makeup".equals(action.type)) {
            status = "taken_makeup";
        }
        
        recordMedicationLogOptimized(medicineId, slotId, now, doseInfo, actualTaken, dosagePerTime, 
            weightBefore, weightAfter, actualWeightDiff, takeResult, "device", status, 
            timeDeviationSeconds, deviceConnected, batteryLevel, calibrationFactor, weightMeasurements);
        
        checkAndCreateStockAlert(userId, medicineId.longValue(), 
            getRemainingStock(medicineId.intValue()), expectedDosage);
        
        Map<String, Object> result = new HashMap<>();
        result.put("medicineName", medicineName);
        result.put("actualTaken", actualTaken);
        result.put("expectedDosage", expectedDosage);
        result.put("takeResult", takeResult);
        result.put("stockAfter", getRemainingStock(medicineId.intValue()));
        result.put("timePeriod", timePeriod);
        result.put("slotId", slotId);
        result.put("userMedicineId", medicineId);
        result.put("takeType", action.type);
        result.put("timeDeviationSeconds", timeDeviationSeconds);
        result.put("isEarly", "early".equals(action.type));
        result.put("isMakeup", "makeup".equals(action.type));
        
        if ("underdose".equals(takeResult)) {
            result.put("needMore", true);
            result.put("message", "服药量不足，请继续取药");
            result.put("detailMessage", "您已取 " + actualTaken + "，本次应服 " + expectedDosage + "，请继续取药。");
            log.info("[优化服药-少服] userId={}, 已取={}, 应取={}", userId, actualTaken, expectedDosage);
        } else if ("overdose".equals(takeResult)) {
            result.put("needRestock", true);
            result.put("message", "服药量过多，请放回多余药品");
            result.put("detailMessage", "您已取 " + actualTaken + "，本次应服 " + expectedDosage + "，请放回多余药品。");
            log.info("[优化服药-多服] userId={}, 已取={}, 应取={}", userId, actualTaken, expectedDosage);
        } else {
            result.put("takeSuccess", true);
            result.put("message", action.message);
            result.put("detailMessage", action.detailMessage);
            log.info("[优化服药-正常] userId={}, 已取={}, 应取={}, type={}", userId, actualTaken, expectedDosage, action.type);
        }
        
        return Result.success(result);
    }
    
    private Map<String, Object> validateDeviceState(Boolean deviceConnected, Boolean boxLocked, MedicineBox box, Integer batteryLevel) {
        if (deviceConnected != null && !deviceConnected) {
            Map<String, Object> error = new HashMap<>();
            error.put("errorType", "device_disconnected");
            error.put("message", "设备连接失败，请检查设备连接状态");
            error.put("detailMessage", "智能药盒当前处于离线状态，无法执行服药操作。请检查设备电源和网络连接后重试。");
            return error;
        }
        
        LocalDateTime now = LocalDateTime.now();
        if ((boxLocked != null && boxLocked) || 
            (box.getIsLocked() != null && box.getIsLocked() && 
             box.getLockedUntil() != null && box.getLockedUntil().isAfter(now))) {
            Map<String, Object> error = new HashMap<>();
            error.put("errorType", "box_locked");
            error.put("message", "智能药盒已锁定");
            error.put("detailMessage", "您已通过手动方式完成服药，智能药盒功能将在下次服药时间解锁。");
            error.put("lockedUntil", box.getLockedUntil() != null ? box.getLockedUntil().toString() : null);
            return error;
        }
        
        if (batteryLevel != null && batteryLevel < 10) {
            Map<String, Object> warning = new HashMap<>();
            warning.put("warningType", "low_battery");
            warning.put("message", "电池电量低");
            warning.put("detailMessage", "智能药盒电池电量不足，请及时充电。");
            warning.put("batteryLevel", batteryLevel);
            log.warn("[设备警告] 电池电量低: {}%", batteryLevel);
        }
        
        return null;
    }
    
    private Map<String, Object> checkDuplicateMedicationOptimized(Long medicineId, PlannedDoseInfo doseInfo, LocalDateTime now) {
        try {
            LocalDateTime scheduledTime = LocalDateTime.of(now.toLocalDate(), doseInfo.plannedTime);
            Map<String, Object> takenLog = checkTakenTodayByUserMedicineAndTime(medicineId.intValue(), scheduledTime);
            
            if (takenLog != null) {
                Map<String, Object> warningData = new HashMap<>();
                warningData.put("duplicate", true);
                warningData.put("message", "您已服用过本次药品，请把药品放回药盒！");
                warningData.put("detailMessage", "您已在 " + 
                    (takenLog.get("taken_time") != null ? takenLog.get("taken_time").toString().substring(11, 16) : "") + 
                    " 服用过该药品，请不要重复服用。");
                warningData.put("medicineId", medicineId);
                warningData.put("takenTime", takenLog.get("taken_time") != null ? takenLog.get("taken_time").toString() : "");
                return warningData;
            }
        } catch (Exception e) {
            log.error("检查重复服药失败", e);
        }
        return null;
    }
    
    private double calculateAverageWeightDiff(List<Double> weightMeasurements, Double defaultDiff) {
        if (weightMeasurements == null || weightMeasurements.isEmpty()) {
            return defaultDiff != null ? defaultDiff : 0.0;
        }
        
        int count = weightMeasurements.size();
        double sum = 0.0;
        for (Double measurement : weightMeasurements) {
            if (measurement != null) {
                sum += measurement;
            } else {
                count--;
            }
        }
        
        return count > 0 ? sum / count : (defaultDiff != null ? defaultDiff : 0.0);
    }
    
    private long calculateTimeDeviation(LocalTime actualTime, LocalTime plannedTime) {
        long actualSeconds = actualTime.toSecondOfDay();
        long plannedSeconds = plannedTime.toSecondOfDay();
        return actualSeconds - plannedSeconds;
    }
    
    private void recordMedicationLogOptimized(Long userMedicineId, Long slotId, LocalDateTime now, 
                                              PlannedDoseInfo doseInfo, BigDecimal actualTaken, 
                                              BigDecimal expectedDosage, Double weightBefore, Double weightAfter,
                                              Double weightDiff, String takeResult, String source, String status,
                                              Long timeDeviationSeconds, Boolean deviceConnected, Integer batteryLevel,
                                              Double calibrationFactor, List<Double> weightMeasurements) {
        try {
            LocalDateTime scheduledTime = LocalDateTime.of(now.toLocalDate(), doseInfo.plannedTime);
            
            Map<String, Object> extendedData = new HashMap<>();
            extendedData.put("timeDeviationSeconds", timeDeviationSeconds);
            extendedData.put("deviceConnected", deviceConnected);
            extendedData.put("batteryLevel", batteryLevel);
            extendedData.put("calibrationFactor", calibrationFactor);
            extendedData.put("weightMeasurements", weightMeasurements);
            
            String extendedDataJson = objectMapper.writeValueAsString(extendedData);
            
            jdbcTemplate.update(
                "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, taken_time, " +
                "weight_before, weight_after, weight_diff, expected_dosage, dosage_taken, take_result, " +
                "status, source, extended_data, create_time) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW())",
                userMedicineId, slotId, scheduledTime, now, weightBefore, weightAfter, weightDiff,
                expectedDosage, actualTaken, takeResult, status, source, extendedDataJson
            );
            
            log.info("[优化日志记录] 服药日志已保存, medicineId={}, takeResult={}, status={}", 
                userMedicineId, takeResult, status);
        } catch (Exception e) {
            log.error("记录优化服药日志失败", e);
        }
    }
    
    private BigDecimal getDosagePerTime(Integer userMedicineId) {
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
        return new BigDecimal("1");
    }
    
    private String judgeCumulativeTakeResult(int actualTake, int expectedDosage) {
        if (actualTake < expectedDosage) {
            return "underdose";
        } else if (actualTake > expectedDosage) {
            return "overdose";
        }
        return "normal";
    }
    
    private void completeMedicationSession(Long userId, Long medicineId, Long slotId, int actualTake, int expectedDosage, 
                                           Double weightBefore, Double weightAfter, Double weightDiff, String finalResult, PlannedDoseInfo doseInfo) {
        jdbcTemplate.update(
            "UPDATE medicine_box_slot SET actual_take_count = 0, expected_take_count = ?, take_match_status = 'match', update_time = NOW() WHERE id = ?",
            expectedDosage, slotId
        );
        
        updateMedicineStock(medicineId.intValue(), actualTake);
        
        String status = "taken";
        String takeResult = "normal";
        
        if (doseInfo != null) {
            LocalTime now = LocalTime.now();
            
            if (now.isBefore(doseInfo.plannedTime)) {
                status = "taken_early";
                takeResult = "early";
            } else {
                long minutesSincePlanned = java.time.Duration.between(doseInfo.plannedTime, now).toMinutes();
                if (minutesSincePlanned > 30) {
                    status = "taken_makeup";
                    takeResult = "makeup";
                }
            }
        }
        
        jdbcTemplate.update(
            "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, taken_time, dosage_taken, " +
            "weight_before, weight_after, weight_diff, expected_dosage, take_result, status, source, create_time) " +
            "VALUES (?, ?, NOW(), NOW(), ?, ?, ?, ?, ?, ?, ?, 'device', NOW())",
            medicineId, slotId, actualTake, weightBefore, weightAfter, weightDiff, expectedDosage, takeResult, status
        );
        
        checkAndCreateStockAlert(userId, medicineId, getRemainingStock(medicineId.intValue()), actualTake);
        
        log.info("[完成服药会话] userId={}, medicineId={}, actualTake={}, expectedDosage={}, takeResult={}", userId, medicineId, actualTake, expectedDosage, takeResult);
    }

    private Result executeTakeMedicine(Long userId, MedicineBox box, Integer slotId, String timePeriod, String takeType) {
        Map<String, Object> slot = getSlotById(slotId.longValue());
        if (slot == null) {
            return Result.error("药仓不存在");
        }

        Integer userMedicineId = null;
        Object medIdObj = slot.get("user_medicine_id");
        if (medIdObj != null) {
            userMedicineId = ((Number) medIdObj).intValue();
        }
        
        String medicineName = (String) slot.get("slot_name");
        
        LocalDateTime now = LocalDateTime.now();
        if (box.getIsLocked() != null && box.getIsLocked() && 
            box.getLockedUntil() != null && box.getLockedUntil().isAfter(now)) {
            log.warn("[智能药盒锁定] 用户 {} 的智能药盒已被锁定至 {}, 锁定至: {}", userId, box.getLockedUntil());
            
            Map<String, Object> warningData = new HashMap<>();
            warningData.put("locked", true);
            warningData.put("message", "智能药盒已锁定");
            warningData.put("detailMessage", "您已通过手动方式完成服药，智能药盒功能将在下次服药时间解锁");
            warningData.put("lockedUntil", box.getLockedUntil().toString());
            
            return Result.error(400, "智能药盒已锁定，请等待下次服药时间", warningData);
        }
        
        if (userMedicineId != null) {
            LocalTime currentTime = now.toLocalTime();
            String reminderTimesJson = getReminderTimes(userMedicineId);
            List<String> reminderTimes = parseReminderTimes(reminderTimesJson);
            
            PlannedDoseInfo doseInfo = getPlannedDoseInfo(reminderTimes, currentTime);
            
            LocalDateTime scheduledTime = LocalDateTime.of(now.toLocalDate(), doseInfo.plannedTime);
            Map<String, Object> takenLog = checkTakenTodayByUserMedicineAndTime(userMedicineId, scheduledTime);
            
            if (takenLog != null) {
                log.warn("[重复服药警告] 用户已服用过该药品, userId={}, medicineId={}, medicineName={}, takenTime={}", 
                    userId, userMedicineId, medicineName, takenLog.get("taken_time"));
                
                Map<String, Object> warningData = new HashMap<>();
                warningData.put("duplicate", true);
                warningData.put("message", "长辈，您已服用过本次药品，请把药品放回药盒！");
                warningData.put("detailMessage", "您已在 " + (takenLog.get("taken_time") != null ? takenLog.get("taken_time").toString().substring(11, 16) : "") + " 服用过该药品，请不要重复服用，请将药品放回药盒。");
                warningData.put("medicineName", medicineName);
                warningData.put("takenTime", takenLog.get("taken_time") != null ? takenLog.get("taken_time").toString() : "");
                
                return Result.error(400, "长辈，您已服用过本次药品，请把药品放回药盒！", warningData);
            }
            
            MedicationAction action = judgeMedicationAction(currentTime, doseInfo);
            
            if ("too_early".equals(action.type)) {
                log.warn("[过早服药拦截] userId={}, medicineId={}, plannedTime={}, currentTime={}", 
                    userId, userMedicineId, doseInfo.plannedTime, currentTime);
                
                Map<String, Object> warningData = new HashMap<>();
                warningData.put("needRestock", true);
                warningData.put("message", "当前不在用药时间，请补充一剂药物");
                warningData.put("detailMessage", "当前不在用药时间段，请将药品放回药盒。点击确认后，系统将记录放回操作。");
                warningData.put("medicineName", medicineName);
                warningData.put("plannedTime", doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                warningData.put("earlyAllowTime", doseInfo.earlyAllowTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                warningData.put("currentTime", currentTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                warningData.put("slotId", slotId);
                warningData.put("userMedicineId", userMedicineId);
                
                return Result.error(400, "当前不在用药时间，请补充一剂药物", warningData);
            }
            
            if ("missed".equals(action.type)) {
                log.warn("[漏服拦截-需放回] userId={}, medicineId={}, plannedTime={}, makeupDeadTime={}, currentTime={}", 
                    userId, userMedicineId, doseInfo.plannedTime, doseInfo.makeupDeadTime, currentTime);
                
                Map<String, Object> warningData = new HashMap<>();
                warningData.put("needRestock", true);
                warningData.put("message", "已错过补服时间，请补充一剂药物");
                warningData.put("detailMessage", "您已错过本次补服窗口，请将药品放回药盒。点击确认后，系统将记录放回操作。");
                warningData.put("medicineName", medicineName);
                warningData.put("plannedTime", doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                warningData.put("makeupDeadTime", doseInfo.makeupDeadTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                warningData.put("currentTime", currentTime.format(DateTimeFormatter.ofPattern("HH:mm")));
                warningData.put("slotId", slotId);
                warningData.put("userMedicineId", userMedicineId);
                
                return Result.error(400, "已错过补服时间，请补充一剂药物", warningData);
            }
        }
        
        BigDecimal dosageWeight = new BigDecimal("1.0");
        Object dosageObj = slot.get("dosage_weight");
        if (dosageObj != null) {
            dosageWeight = new BigDecimal(dosageObj.toString());
        }
        
        BigDecimal weightBefore = new BigDecimal("0.00");
        Object currentWeightObj = slot.get("current_weight");
        if (currentWeightObj != null) {
            weightBefore = new BigDecimal(currentWeightObj.toString());
        }
        
        BigDecimal actualTaken;
        if ("underdose".equals(takeType)) {
            actualTaken = dosageWeight.multiply(new BigDecimal("0.4")).setScale(2, RoundingMode.HALF_UP);
        } else if ("overdose".equals(takeType)) {
            actualTaken = dosageWeight.multiply(new BigDecimal("1.8")).setScale(2, RoundingMode.HALF_UP);
        } else {
            Random random = new Random();
            double variation = random.nextDouble() * 0.2 - 0.1;
            actualTaken = dosageWeight.multiply(new BigDecimal(1 + variation)).setScale(2, RoundingMode.HALF_UP);
        }
        
        BigDecimal weightAfter = weightBefore.subtract(actualTaken).max(BigDecimal.ZERO);
        String takeResult = calculateTakeResult(actualTaken, dosageWeight);
        
        updateSlotAfterTake(slotId, weightBefore, weightAfter, takeResult);
        
        String status = "taken";
        if (userMedicineId != null) {
            int takenAmount = actualTaken.setScale(0, RoundingMode.HALF_UP).intValue();
            updateMedicineStock(userMedicineId, takenAmount);
            checkAndCreateStockAlert(userId, userMedicineId.longValue(), 
                getRemainingStock(userMedicineId), dosageWeight.intValue());
            
            LocalTime currentTime = now.toLocalTime();
            String reminderTimesJson = getReminderTimes(userMedicineId);
            List<String> reminderTimes = parseReminderTimes(reminderTimesJson);
            
            PlannedDoseInfo doseInfo = getPlannedDoseInfo(reminderTimes, currentTime);
            MedicationAction action = judgeMedicationAction(currentTime, doseInfo);
            
            if ("early".equals(action.type)) {
                status = "taken_early";
            } else if ("makeup".equals(action.type)) {
                status = "taken_makeup";
            }
        }
        
        recordMedicationLogWithWeight(userMedicineId, slotId, weightBefore, weightAfter, dosageWeight, takeResult, "box", status);
        recordBoxEvent(box.getDeviceId(), userId, "takeout", userMedicineId, createPayload(slotId, weightBefore, weightAfter, timePeriod));

        box.setLastSyncTime(LocalDateTime.now());
        box.setIsLocked(false);
        box.setLockedUntil(null);
        medicineBoxMapper.update(box);

        log.info("[模拟服药] 用户ID: {}, 药仓ID: {}, 时段: {}, 结果: {}", userId, slotId, timePeriod, takeResult);

        Map<String, Object> result = new HashMap<>();
        result.put("message", "模拟服药成功");
        result.put("slotId", slotId);
        result.put("slotName", slot.get("slot_name"));
        result.put("timePeriod", timePeriod);
        result.put("weightBefore", weightBefore);
        result.put("weightAfter", weightAfter);
        result.put("actualTaken", actualTaken);
        result.put("expectedDosage", dosageWeight);
        result.put("takeResult", takeResult);
        
        return Result.success(result);
    }

    private Map<String, Object> checkDuplicateMedication(Integer userMedicineId, String timePeriod) {
        try {
            String sql = "SELECT ml.id, ml.taken_time, ml.scheduled_time, ml.source, ml.status " +
                        "FROM medication_logs ml " +
                        "WHERE ml.user_medicine_id = ? " +
                        "AND ml.status = 'taken' " +
                        "AND DATE(ml.taken_time) = CURDATE() " +
                        "ORDER BY ml.taken_time DESC LIMIT 1";
            
            List<Map<String, Object>> logs = jdbcTemplate.queryForList(sql, userMedicineId);
            
            if (!logs.isEmpty()) {
                Map<String, Object> lastLog = logs.get(0);
                LocalDateTime takenTime = (LocalDateTime) lastLog.get("taken_time");
                
                if (takenTime != null) {
                    long minutesSinceLastTake = java.time.Duration.between(takenTime, LocalDateTime.now()).toMinutes();
                    
                    if (minutesSinceLastTake < 60) {
                        return lastLog;
                    }
                }
            }
            
            String pendingSql = "SELECT ml.id, ml.scheduled_time, ml.status " +
                               "FROM medication_logs ml " +
                               "WHERE ml.user_medicine_id = ? " +
                               "AND ml.status = 'taken' " +
                               "AND DATE(ml.scheduled_time) = CURDATE()";
            
            List<Map<String, Object>> todayLogs = jdbcTemplate.queryForList(pendingSql, userMedicineId);
            
            if (!todayLogs.isEmpty()) {
                LocalTime now = LocalTime.now();
                String reminderTimesJson = getReminderTimes(userMedicineId);
                
                if (reminderTimesJson != null) {
                    com.alibaba.fastjson2.JSONArray times = com.alibaba.fastjson2.JSON.parseArray(reminderTimesJson);
                    if (times != null) {
                        for (int i = 0; i < times.size(); i++) {
                            String timeStr = times.getString(i);
                            try {
                                LocalTime reminderTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                                long minutesDiff = Math.abs(java.time.Duration.between(reminderTime, now).toMinutes());
                                
                                if (minutesDiff < 60) {
                                    for (Map<String, Object> log : todayLogs) {
                                        LocalDateTime scheduledTime = (LocalDateTime) log.get("scheduled_time");
                                        if (scheduledTime != null && 
                                            scheduledTime.toLocalTime().equals(reminderTime)) {
                                            return log;
                                        }
                                    }
                                }
                            } catch (Exception e) {
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("检查重复服药失败", e);
        }
        return null;
    }

    private String getReminderTimes(Integer userMedicineId) {
        try {
            Map<String, Object> medicine = jdbcTemplate.queryForMap(
                "SELECT reminder_times FROM user_medicines WHERE id = ?", userMedicineId
            );
            return medicine != null ? (String) medicine.get("reminder_times") : null;
        } catch (Exception e) {
            return null;
        }
    }

    private void notifyDuplicateMedication(Long userId, Integer userMedicineId, String medicineName, String timePeriod, Map<String, Object> lastLog) {
        try {
            List<Map<String, Object>> familyMembers = jdbcTemplate.queryForList(
                "SELECT user_id FROM friendships WHERE friend_id = ? AND status = 1",
                userId
            );
            
            String elderName = getElderName(userId);
            
            for (Map<String, Object> family : familyMembers) {
                Long familyUserId = ((Number) family.get("user_id")).longValue();
                
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "DUPLICATE_MEDICATION");
                notification.put("elderId", userId);
                notification.put("elderName", elderName);
                notification.put("medicineName", medicineName);
                notification.put("timePeriod", timePeriod);
                notification.put("lastTakenTime", lastLog.get("taken_time"));
                notification.put("timestamp", System.currentTimeMillis());
                notification.put("content", elderName + " 尝试重复服用 " + medicineName + "，已阻止操作");
                
                jdbcTemplate.update(
                    "INSERT INTO notifications (user_id, type, title, content, data, is_read, create_time) " +
                    "VALUES (?, 'DUPLICATE_MEDICATION', '重复服药警告', ?, ?, 0, NOW())",
                    familyUserId, elderName + " 尝试重复服用 " + medicineName, objectMapper.writeValueAsString(notification)
                );
            }
            
            log.info("[重复服药通知] 已通知家属, userId={}, medicineName={}", userId, medicineName);
        } catch (Exception e) {
            log.error("通知家属重复服药事件失败", e);
        }
    }

    private String getElderName(Long userId) {
        try {
            Map<String, Object> user = jdbcTemplate.queryForMap(
                "SELECT nickname FROM users WHERE id = ?", userId
            );
            return user != null ? (String) user.get("nickname") : "用户";
        } catch (Exception e) {
            return "用户";
        }
    }

    public Result unbindSlot(Long slotId) {
        int updated = jdbcTemplate.update(
            "UPDATE medicine_box_slot SET user_medicine_id = NULL, slot_name = NULL, " +
            "dosage_weight = NULL, current_weight = 0, take_status = 'pending', update_time = NOW() WHERE id = ?",
            slotId
        );
        
        if (updated > 0) {
            return Result.success("解绑成功");
        }
        return Result.error("解绑失败");
    }

    public Result autoBindSlots(Long userId) {
        MedicineBox box = medicineBoxMapper.selectByUserId(userId);
        if (box == null) {
            box = createDefaultBox(userId, "BOX" + userId);
        }
        
        List<Map<String, Object>> userMedicines = jdbcTemplate.queryForList(
            "SELECT id, custom_name, reminder_times, dosage_per_time, current_stock FROM user_medicines WHERE user_id = ? AND is_deleted = 0 ORDER BY id",
            userId
        );
        
        List<Map<String, Object>> slots = jdbcTemplate.queryForList(
            "SELECT id, slot_number FROM medicine_box_slot WHERE box_id = ? ORDER BY slot_number",
            box.getId()
        );
        
        if (slots.isEmpty()) {
            initBoxSlots(box.getId());
            slots = jdbcTemplate.queryForList(
                "SELECT id, slot_number FROM medicine_box_slot WHERE box_id = ? ORDER BY slot_number",
                box.getId()
            );
        }
        
        int bindCount = 0;
        
        for (int i = 0; i < userMedicines.size() && i < slots.size(); i++) {
            Map<String, Object> medicine = userMedicines.get(i);
            Map<String, Object> slot = slots.get(i);
            
            String medicineName = (String) medicine.get("custom_name");
            Integer medicineId = ((Number) medicine.get("id")).intValue();
            int currentStock = medicine.get("current_stock") != null ? 
                ((Number) medicine.get("current_stock")).intValue() : 0;
            
            BigDecimal dosagePerTime = medicine.get("dosage_per_time") != null ? 
                new BigDecimal(medicine.get("dosage_per_time").toString()) : new BigDecimal("1");
            BigDecimal currentWeight = new BigDecimal(currentStock).multiply(GRAM_PER_PILL);
            
            jdbcTemplate.update(
                "UPDATE medicine_box_slot SET user_medicine_id = ?, slot_name = ?, " +
                "dosage_weight = ?, current_weight = ?, take_status = 'pending', update_time = NOW() WHERE id = ?",
                medicineId, medicineName != null ? medicineName : "药品" + (i + 1), 
                dosagePerTime, currentWeight, slot.get("id")
            );
            
            bindCount++;
        }
        
        return Result.success("自动绑定完成，共绑定 " + bindCount + " 种药品");
    }

    private String getCurrentTimePeriod(LocalTime time) {
        int hour = time.getHour();
        if (hour >= 5 && hour < 11) {
            return "早";
        } else if (hour >= 11 && hour < 14) {
            return "中";
        } else if (hour >= 17 && hour < 20) {
            return "晚";
        } else if (hour >= 20 || hour < 5) {
            return "睡前";
        }
        return "其他";
    }
    
    private String getCurrentTimePeriodByReminderTimes(LocalTime currentTime, List<String> reminderTimes) {
        if (reminderTimes == null || reminderTimes.isEmpty()) {
            return getCurrentTimePeriod(currentTime);
        }
        
        List<LocalTime> sortedTimes = new ArrayList<>();
        Map<LocalTime, String> timeToPeriodMap = new HashMap<>();
        
        for (String timeStr : reminderTimes) {
            try {
                LocalTime reminderTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                sortedTimes.add(reminderTime);
                timeToPeriodMap.put(reminderTime, getTimePeriodNameByTime(timeStr));
            } catch (Exception e) {
            }
        }
        Collections.sort(sortedTimes);
        
        if (sortedTimes.isEmpty()) {
            return getCurrentTimePeriod(currentTime);
        }
        
        // 使用我们修复好的统一逻辑
        PlannedDoseInfo doseInfo = getPlannedDoseInfo(reminderTimes, currentTime);
        
        // 返回计划时间对应的时间段
        return timeToPeriodMap.getOrDefault(doseInfo.plannedTime, getCurrentTimePeriod(currentTime));
    }
    
    private boolean isInMedicationWindow(LocalTime currentTime, List<String> reminderTimes) {
        if (reminderTimes == null || reminderTimes.isEmpty()) {
            return false;
        }
        
        // 使用我们修复好的统一逻辑
        PlannedDoseInfo doseInfo = getPlannedDoseInfo(reminderTimes, currentTime);
        
        // 检查当前时间是否在服药窗口内
        if (currentTime.isBefore(doseInfo.earlyAllowTime)) {
            return false;
        }
        
        if (currentTime.isAfter(doseInfo.makeupDeadTime)) {
            return false;
        }
        
        return true;
    }

    private void checkAndCreateStockAlert(Long userId, Long medicineId, int currentStock, int dosagePerTime) {
        String medicineName = getMedicineName(medicineId);
        
        if (currentStock <= dosagePerTime) {
            createStockAlert(medicineId, medicineName, currentStock, "low_stock", 3, 
                "药品即将用完，仅剩 " + currentStock + " 粒，请及时补充！");
        } else {
            int frequencyPerDay = getFrequencyPerDay(medicineId);
            int remainingDays = frequencyPerDay > 0 ? currentStock / (dosagePerTime * frequencyPerDay) : currentStock / dosagePerTime;
            
            if (remainingDays <= 3) {
                createStockAlert(medicineId, medicineName, currentStock, "low_stock", 2, 
                    "药品余量不足，预计可服用 " + remainingDays + " 天，请及时补充！");
            }
        }
    }

    private void createStockAlert(Long medicineId, String medicineName, int currentStock, String alertType, int alertLevel, String message) {
        Integer existingAlert = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM medicine_stock_alert WHERE user_medicine_id = ? AND alert_type = ? AND is_resolved = 0",
            Integer.class, medicineId, alertType
        );
        
        if (existingAlert != null && existingAlert > 0) {
            return;
        }
        
        jdbcTemplate.update(
            "INSERT INTO medicine_stock_alert (user_medicine_id, alert_type, alert_level, message, days_remaining, is_read, is_resolved) VALUES (?, ?, ?, ?, ?, 0, 0)",
            medicineId, alertType, alertLevel, message, currentStock
        );
        
        log.info("[库存预警] 药品ID: {}, 类型: {}, 消息: {}", medicineId, alertType, message);
    }

    private String getMedicineName(Long medicineId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT custom_name FROM user_medicines WHERE id = ?", String.class, medicineId
            );
        } catch (Exception e) {
            return "未知药品";
        }
    }

    private int getFrequencyPerDay(Long medicineId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT frequency_per_day FROM user_medicines WHERE id = ?", Integer.class, medicineId
            );
        } catch (Exception e) {
            return 2;
        }
    }

    private int getRemainingStock(Integer medicineId) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT current_stock FROM user_medicines WHERE id = ?", Integer.class, medicineId
            );
        } catch (Exception e) {
            return 0;
        }
    }

    private void updateMedicineStock(Integer medicineId, int takenAmount) {
        Integer stockBefore = jdbcTemplate.queryForObject(
            "SELECT current_stock FROM user_medicines WHERE id = ?",
            Integer.class, medicineId
        );
        
        if (stockBefore == null) {
            log.warn("[药盒服药] 药品不存在, medicineId={}", medicineId);
            return;
        }
        
        if (stockBefore < takenAmount) {
            log.warn("[药盒服药] 库存不足, medicineId={}, stock={}, taken={}", medicineId, stockBefore, takenAmount);
            return;
        }
        
        int rowsAffected = jdbcTemplate.update(
            "UPDATE user_medicines SET current_stock = current_stock - ?, update_time = NOW() WHERE id = ? AND current_stock >= ?",
            takenAmount, medicineId, takenAmount
        );
        
        if (rowsAffected > 0) {
            Integer stockAfter = stockBefore - takenAmount;
            log.info("[药盒服药] 库存更新成功, medicineId={}, stockBefore={}, takenAmount={}, stockAfter={}", 
                medicineId, stockBefore, takenAmount, stockAfter);
            
            if (stockAfter == 0) {
                resetSlotWeightWhenStockEmpty(medicineId);
            }
        } else {
            log.error("[药盒服药] 库存更新失败（并发冲突）, medicineId={}, stock={}, taken={}", medicineId, stockBefore, takenAmount);
        }
    }

    private void resetSlotWeightWhenStockEmpty(Integer medicineId) {
        try {
            List<Map<String, Object>> slots = jdbcTemplate.queryForList(
                "SELECT id, slot_name, current_weight FROM medicine_box_slot WHERE user_medicine_id = ?",
                medicineId
            );
            
            if (slots.isEmpty()) {
                log.info("[库存归零] 药品未绑定药仓, medicineId={}", medicineId);
                return;
            }
            
            for (Map<String, Object> slot : slots) {
                Integer slotId = ((Number) slot.get("id")).intValue();
                String slotName = (String) slot.get("slot_name");
                Object currentWeight = slot.get("current_weight");
                
                int updated = jdbcTemplate.update(
                    "UPDATE medicine_box_slot SET current_weight = 0, take_status = 'empty', update_time = NOW() WHERE id = ?",
                    slotId
                );
                
                if (updated > 0) {
                    log.info("[库存归零] 药仓剩余克数已归零, medicineId={}, slotId={}, slotName={}, previousWeight={}", 
                        medicineId, slotId, slotName, currentWeight);
                }
            }
            
            log.info("[库存归零] 完成, medicineId={}, 归零药仓数量={}", medicineId, slots.size());
            
        } catch (Exception e) {
            log.error("[库存归零] 操作失败, medicineId={}", medicineId, e);
        }
    }

    private MedicineBox createDefaultBox(Long userId, String deviceId) {
        MedicineBox box = new MedicineBox();
        box.setUserId(userId);
        box.setDeviceId(deviceId != null ? deviceId : "BOX" + System.currentTimeMillis());
        box.setBoxName("智能药盒");
        box.setStatus(1);
        box.setBatteryLevel(100);
        box.setLastSyncTime(LocalDateTime.now());
        
        medicineBoxMapper.insert(box);
        initBoxSlots(box.getId());
        
        return box;
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

    private List<Map<String, Object>> getUserMedicinesWithStock(Long userId) {
        return jdbcTemplate.queryForList(
            "SELECT id, custom_name, reminder_times, dosage_per_time, total_stock, current_stock, frequency_per_day, " +
            "CASE WHEN current_stock <= dosage_per_time THEN 'empty' " +
            "WHEN current_stock <= (dosage_per_time * frequency_per_day * 3) THEN 'low' ELSE 'normal' END as stock_status " +
            "FROM user_medicines WHERE user_id = ? AND is_deleted = 0 ORDER BY id",
            userId
        );
    }

    private List<Map<String, Object>> getStockAlerts(Long userId) {
        return jdbcTemplate.queryForList(
            "SELECT a.*, um.custom_name as medicine_name FROM medicine_stock_alert a " +
            "JOIN user_medicines um ON a.user_medicine_id = um.id " +
            "WHERE um.user_id = ? AND um.is_deleted = 0 AND a.is_resolved = 0 ORDER BY a.create_time DESC LIMIT 10",
            userId
        );
    }

    private List<Map<String, Object>> getBoxSlotsWithMedicineInfo(Long boxId) {
        List<Map<String, Object>> slots = jdbcTemplate.queryForList(
            "SELECT s.*, um.custom_name, um.reminder_times, um.dosage_per_time, um.frequency_per_day, um.current_stock " +
            "FROM medicine_box_slot s " +
            "LEFT JOIN user_medicines um ON s.user_medicine_id = um.id " +
            "WHERE s.box_id = ? ORDER BY s.slot_number",
            boxId
        );
        
        Map<String, Object> box = jdbcTemplate.queryForMap(
            "SELECT single_dose_weight, full_bottle_weight, tolerance_min, tolerance_max FROM medicine_box WHERE id = ?",
            boxId
        );
        
        BigDecimal singleDoseWeight = box.get("single_dose_weight") != null ?
            new BigDecimal(box.get("single_dose_weight").toString()) : null;
        
        BigDecimal emptyWeight = box.get("full_bottle_weight") != null ?
            new BigDecimal(box.get("full_bottle_weight").toString()).multiply(new BigDecimal("0.1")) :
            new BigDecimal("5.0");
        
        BigDecimal maxDoseWeight = box.get("tolerance_max") != null ?
            new BigDecimal(box.get("tolerance_max").toString()) :
            (singleDoseWeight != null ? singleDoseWeight.multiply(new BigDecimal("1.15")) : new BigDecimal("10"));
        
        for (Map<String, Object> slot : slots) {
            String reminderTimes = (String) slot.get("reminder_times");
            List<Map<String, Object>> timeDosages = parseTimeDosages(reminderTimes);
            slot.put("timeDosages", timeDosages);
            slot.put("nextReminderTime", getNextReminderTime(reminderTimes));
            
            BigDecimal currentWeight = slot.get("current_weight") != null ? 
                new BigDecimal(slot.get("current_weight").toString()) : BigDecimal.ZERO;
            BigDecimal dosageWeight = slot.get("dosage_weight") != null ? 
                new BigDecimal(slot.get("dosage_weight").toString()) : new BigDecimal("1");
            
            if (currentWeight.compareTo(BigDecimal.ZERO) <= 0) {
                slot.put("stock_status", "empty");
            } else if (currentWeight.compareTo(dosageWeight.multiply(new BigDecimal("3"))) <= 0) {
                slot.put("stock_status", "low");
            } else {
                slot.put("stock_status", "normal");
            }
            
            Integer remainingPills = slot.get("remaining_pills") != null ?
                ((Number) slot.get("remaining_pills")).intValue() : null;
            Integer availableDoses = slot.get("available_doses") != null ?
                ((Number) slot.get("available_doses")).intValue() : null;
            Integer availableDays = slot.get("available_days") != null ?
                ((Number) slot.get("available_days")).intValue() : null;
            Integer pillsPerDose = slot.get("dosage_per_time") != null ?
                new BigDecimal(slot.get("dosage_per_time").toString()).intValue() : 1;
            Integer currentStock = slot.get("current_stock") != null ?
                ((Number) slot.get("current_stock")).intValue() : 0;
            Integer frequencyPerDay = slot.get("frequency_per_day") != null ?
                ((Number) slot.get("frequency_per_day")).intValue() : 1;
            
            if (slot.get("user_medicine_id") != null) {
                int calculatedAvailableDoses = pillsPerDose > 0 ? currentStock / pillsPerDose : 0;
                calculatedAvailableDoses = Math.max(0, calculatedAvailableDoses);
                
                int calculatedRemainingPills = calculatedAvailableDoses * pillsPerDose;
                
                int calculatedAvailableDays = frequencyPerDay > 0 ? calculatedAvailableDoses / frequencyPerDay : 0;
                
                slot.put("remainingPills", calculatedRemainingPills);
                slot.put("availableDoses", calculatedAvailableDoses);
                slot.put("availableDays", calculatedAvailableDays);
                slot.put("dosage_pills", pillsPerDose);
                slot.put("pillsPerDose", pillsPerDose);
                slot.put("frequencyPerDay", frequencyPerDay);
            } else if (remainingPills != null && availableDoses != null) {
                slot.put("remainingPills", remainingPills);
                slot.put("availableDoses", availableDoses);
                slot.put("availableDays", availableDays != null ? availableDays : 0);
                slot.put("dosage_pills", pillsPerDose);
            } else if (singleDoseWeight != null) {
                BigDecimal actualMedicineWeight = currentWeight.subtract(emptyWeight).max(BigDecimal.ZERO);
                
                int calculatedAvailableDoses = actualMedicineWeight.divide(maxDoseWeight, 0, java.math.RoundingMode.DOWN).intValue();
                calculatedAvailableDoses = Math.max(0, calculatedAvailableDoses);
                
                int calculatedRemainingPills = calculatedAvailableDoses * pillsPerDose;
                
                int calculatedAvailableDays = frequencyPerDay > 0 ? calculatedAvailableDoses / frequencyPerDay : 0;
                
                slot.put("remainingPills", calculatedRemainingPills);
                slot.put("availableDoses", calculatedAvailableDoses);
                slot.put("availableDays", calculatedAvailableDays);
                slot.put("actualMedicineWeight", actualMedicineWeight);
                slot.put("emptyWeight", emptyWeight);
                slot.put("maxDoseWeight", maxDoseWeight);
                slot.put("dosage_pills", pillsPerDose);
                slot.put("pillsPerDose", pillsPerDose);
                slot.put("frequencyPerDay", frequencyPerDay);
            } else {
                slot.put("remainingPills", currentStock);
                slot.put("dosage_pills", pillsPerDose);
            }
        }
        
        return slots;
    }

    private List<Map<String, Object>> parseTimeDosages(String reminderTimes) {
        List<Map<String, Object>> result = new ArrayList<>();
        
        String[] defaultTimes = {"08:00", "18:00"};
        
        List<String> times = new ArrayList<>();
        if (reminderTimes != null && reminderTimes.contains(":")) {
            try {
                times = objectMapper.readValue(reminderTimes, List.class);
            } catch (Exception e) {
                times = Arrays.asList(defaultTimes);
            }
        } else {
            times = Arrays.asList(defaultTimes);
        }
        
        for (int i = 0; i < times.size(); i++) {
            Map<String, Object> td = new HashMap<>();
            String timeStr = times.get(i);
            td.put("period", getTimePeriodNameByTime(timeStr));
            td.put("time", timeStr);
            result.add(td);
        }
        
        return result;
    }
    
    private String getTimePeriodNameByTime(String timeStr) {
        try {
            LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
            int hour = time.getHour();
            int minute = time.getMinute();
            String timeDisplay = String.format("%02d:%02d", hour, minute);
            
            if (hour >= 5 && hour < 11) {
                return "早(" + timeDisplay + ")";
            } else if (hour >= 11 && hour < 14) {
                return "中(" + timeDisplay + ")";
            } else if (hour >= 14 && hour < 17) {
                return "下午(" + timeDisplay + ")";
            } else if (hour >= 17 && hour < 20) {
                return "晚(" + timeDisplay + ")";
            } else if (hour >= 20 || hour < 5) {
                return "睡前(" + timeDisplay + ")";
            }
            return timeDisplay;
        } catch (Exception e) {
            return timeStr;
        }
    }

    private Map<String, Object> getSlotById(Long slotId) {
        List<Map<String, Object>> slots = jdbcTemplate.queryForList(
            "SELECT s.*, um.custom_name FROM medicine_box_slot s " +
            "LEFT JOIN user_medicines um ON s.user_medicine_id = um.id " +
            "WHERE s.id = ?",
            slotId
        );
        return slots.isEmpty() ? null : slots.get(0);
    }

    public Map<String, Object> getRealtimeMedicationStatus(Long userId) {
        Map<String, Object> result = new HashMap<>();
        result.put("userId", userId);
        result.put("timestamp", LocalDateTime.now());
        
        List<Map<String, Object>> medicines = jdbcTemplate.queryForList(
            "SELECT um.id, um.custom_name, um.reminder_times, um.current_stock, um.dosage_per_time, " +
            "um.dose_unit, um.frequency_per_day " +
            "FROM user_medicines um " +
            "WHERE um.user_id = ? AND (um.is_deleted IS NULL OR um.is_deleted = 0) AND um.status = 1",
            userId
        );
        
        List<Map<String, Object>> medicineStatusList = new ArrayList<>();
        
        for (Map<String, Object> medicine : medicines) {
            Integer medicineId = ((Number) medicine.get("id")).intValue();
            String medicineName = (String) medicine.get("custom_name");
            String reminderTimesJson = (String) medicine.get("reminder_times");
            
            Map<String, Object> medicineStatus = new HashMap<>();
            medicineStatus.put("medicineId", medicineId);
            medicineStatus.put("medicineName", medicineName);
            medicineStatus.put("currentStock", medicine.get("current_stock"));
            
            List<Map<String, Object>> todayRecords = jdbcTemplate.queryForList(
                "SELECT ml.id, ml.scheduled_time, ml.taken_time, ml.status, ml.source, ml.take_result " +
                "FROM medication_logs ml " +
                "WHERE ml.user_medicine_id = ? " +
                "AND DATE(ml.scheduled_time) = CURDATE() " +
                "ORDER BY ml.scheduled_time ASC",
                medicineId
            );
            
            List<Map<String, Object>> timeSlotStatus = new ArrayList<>();
            LocalTime now = LocalTime.now();
            
            if (reminderTimesJson != null) {
                try {
                    com.alibaba.fastjson2.JSONArray times = com.alibaba.fastjson2.JSON.parseArray(reminderTimesJson);
                    if (times != null) {
                        for (int i = 0; i < times.size(); i++) {
                            String timeStr = times.getString(i);
                            LocalTime reminderTime = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                            
                            Map<String, Object> slotStatus = new HashMap<>();
                            slotStatus.put("scheduledTime", timeStr);
                            slotStatus.put("period", getTimePeriodNameByTime(timeStr));
                            
                            boolean found = false;
                            for (Map<String, Object> record : todayRecords) {
                                LocalDateTime scheduledTime = (LocalDateTime) record.get("scheduled_time");
                                if (scheduledTime != null && scheduledTime.toLocalTime().equals(reminderTime)) {
                                    slotStatus.put("status", record.get("status"));
                                    slotStatus.put("takenTime", record.get("taken_time"));
                                    slotStatus.put("source", record.get("source"));
                                    slotStatus.put("takeResult", record.get("take_result"));
                                    found = true;
                                    break;
                                }
                            }
                            
                            if (!found) {
                                if (now.isAfter(reminderTime)) {
                                    long minutesSince = java.time.Duration.between(reminderTime, now).toMinutes();
                                    if (minutesSince > 60) {
                                        if (reminderTimesJson != null && canStillMakeup(reminderTimesJson, now)) {
                                            slotStatus.put("status", "pending_take");
                                        } else {
                                            slotStatus.put("status", "missed");
                                        }
                                    } else {
                                        slotStatus.put("status", "pending_take");
                                    }
                                } else {
                                    slotStatus.put("status", "upcoming");
                                }
                            }
                            
                            timeSlotStatus.add(slotStatus);
                        }
                    }
                } catch (Exception e) {
                    log.error("解析提醒时间失败", e);
                }
            }
            
            medicineStatus.put("timeSlots", timeSlotStatus);
            
            List<String> reminderTimes = parseReminderTimes(reminderTimesJson);
            boolean canTakeNow = isInMedicationWindow(now, reminderTimes);
            String canTakeReason = "";
            
            for (Map<String, Object> slot : timeSlotStatus) {
                String status = (String) slot.get("status");
                if ("taken".equals(status) || "taken_early".equals(status) || "taken_makeup".equals(status)) {
                    canTakeNow = false;
                    canTakeReason = "当前时段已服药，请勿重复服用";
                    break;
                }
            }
            
            if (canTakeNow && canTakeReason.isEmpty()) {
                canTakeReason = "当前时段可以服药";
            } else if (!canTakeNow && canTakeReason.isEmpty()) {
                canTakeReason = "当前不在服药时间窗口内";
            }
            
            medicineStatus.put("canTakeNow", canTakeNow);
            medicineStatus.put("canTakeReason", canTakeReason);
            
            medicineStatusList.add(medicineStatus);
        }
        
        result.put("medicines", medicineStatusList);
        
        boolean hasDuplicateRisk = false;
        for (Map<String, Object> med : medicineStatusList) {
            if (Boolean.FALSE.equals(med.get("canTakeNow"))) {
                hasDuplicateRisk = true;
                break;
            }
        }
        result.put("hasDuplicateRisk", hasDuplicateRisk);
        
        return result;
    }

    private String getTimePeriodName(LocalTime time) {
        int hour = time.getHour();
        if (hour >= 5 && hour < 11) {
            return "早";
        } else if (hour >= 11 && hour < 14) {
            return "中";
        } else if (hour >= 17 && hour < 20) {
            return "晚";
        } else if (hour >= 20 || hour < 5) {
            return "睡前";
        }
        return "其他";
    }

    private Map<String, Object> findSlotWithMedicine(Long boxId) {
        List<Map<String, Object>> slots = jdbcTemplate.queryForList(
            "SELECT s.*, um.custom_name FROM medicine_box_slot s " +
            "LEFT JOIN user_medicines um ON s.user_medicine_id = um.id " +
            "WHERE s.box_id = ? AND s.user_medicine_id IS NOT NULL " +
            "ORDER BY s.slot_number LIMIT 1",
            boxId
        );
        return slots.isEmpty() ? null : slots.get(0);
    }

    private String calculateTakeResult(BigDecimal actualTaken, BigDecimal expectedDosage) {
        return calculateTakeResult(actualTaken, expectedDosage, BigDecimal.ONE);
    }
    
    private String calculateTakeResult(BigDecimal actualTaken, BigDecimal expectedDosage, BigDecimal calibrationFactor) {
        BigDecimal calibratedActual = actualTaken.multiply(calibrationFactor);
        BigDecimal lowerThreshold = expectedDosage.multiply(new BigDecimal("0.85"));
        BigDecimal upperThreshold = expectedDosage.multiply(new BigDecimal("1.15"));
        
        if (calibratedActual.compareTo(lowerThreshold) < 0) {
            return "underdose";
        } else if (calibratedActual.compareTo(upperThreshold) > 0) {
            return "overdose";
        }
        return "normal";
    }

    private void updateSlotAfterTake(Integer slotId, BigDecimal weightBefore, BigDecimal weightAfter, String takeResult) {
        String status = "normal".equals(takeResult) ? "taken" : "abnormal";
        jdbcTemplate.update(
            "UPDATE medicine_box_slot SET current_weight = ?, weight_before_take = ?, weight_after_take = ?, " +
            "take_status = ?, last_take_time = NOW(), update_time = NOW() WHERE id = ?",
            weightAfter, weightBefore, weightAfter, status, slotId
        );
    }

    private void recordBoxEvent(String deviceId, Long userId, String eventType, Integer medicineId, Map<String, Object> payload) {
        try {
            String payloadJson = payload != null ? objectMapper.writeValueAsString(payload) : null;
            jdbcTemplate.update(
                "INSERT INTO box_events (device_id, user_id, event_type, medicine_id, event_time, payload) VALUES (?, ?, ?, ?, NOW(), ?)",
                deviceId, userId, eventType, medicineId, payloadJson
            );
        } catch (Exception e) {
            log.error("记录药盒事件失败", e);
        }
    }

    private void recordMedicationLogWithWeight(Integer userMedicineId, Integer slotId, BigDecimal weightBefore, 
                                               BigDecimal weightAfter, BigDecimal expectedDosage, String takeResult, String source, String status) {
        try {
            BigDecimal weightDiff = weightBefore.subtract(weightAfter);
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime scheduledTime = now;
            
            if (userMedicineId != null) {
                String reminderTimesJson = getReminderTimes(userMedicineId);
                List<String> reminderTimes = parseReminderTimes(reminderTimesJson);
                PlannedDoseInfo doseInfo = getPlannedDoseInfo(reminderTimes, now.toLocalTime());
                scheduledTime = LocalDateTime.of(now.toLocalDate(), doseInfo.plannedTime);
            }
            
            jdbcTemplate.update(
                "INSERT INTO medication_logs (user_medicine_id, slot_id, scheduled_time, taken_time, weight_before, weight_after, " +
                "weight_diff, expected_dosage, take_result, status, source) VALUES (?, ?, ?, NOW(), ?, ?, ?, ?, ?, ?, ?)",
                userMedicineId, slotId, scheduledTime, weightBefore, weightAfter, weightDiff, expectedDosage, takeResult, status, source
            );
        } catch (Exception e) {
            log.error("记录服药日志失败", e);
        }
    }

    private Map<String, Object> createPayload(Integer slotId, BigDecimal weightBefore, BigDecimal weightAfter, String timePeriod) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("slotId", slotId);
        payload.put("weightBefore", weightBefore);
        payload.put("weightAfter", weightAfter);
        payload.put("timePeriod", timePeriod);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }

    public Result registerBox(Long userId, String deviceId, String boxName) {
        MedicineBox existing = medicineBoxMapper.selectByDeviceId(deviceId);
        if (existing != null) {
            return Result.error("设备已被注册");
        }

        MedicineBox box = new MedicineBox();
        box.setUserId(userId);
        box.setDeviceId(deviceId);
        box.setBoxName(boxName != null ? boxName : "智能药盒");
        box.setStatus(1);
        box.setBatteryLevel(100);
        box.setLastSyncTime(LocalDateTime.now());
        
        medicineBoxMapper.insert(box);
        initBoxSlots(box.getId());

        return Result.success(box);
    }

    public Result bindSlotToTimeSlot(Long slotId, String timeSlot, String slotName, BigDecimal dosageWeight) {
        int updated = jdbcTemplate.update(
            "UPDATE medicine_box_slot SET bind_time_slot = ?, slot_name = ?, dosage_weight = ?, update_time = NOW() WHERE id = ?",
            timeSlot, slotName, dosageWeight, slotId
        );
        
        if (updated > 0) {
            return Result.success("绑定成功");
        }
        return Result.error("绑定失败");
    }

    public Result setTimeSlot(Long userId, String slotName, String timePoint, Integer graceMinutes) {
        jdbcTemplate.update(
            "INSERT INTO medicine_time_slot (user_id, slot_name, time_point, grace_minutes) VALUES (?, ?, ?, ?) " +
            "ON DUPLICATE KEY UPDATE time_point = VALUES(time_point), grace_minutes = VALUES(grace_minutes)",
            userId, slotName, timePoint, graceMinutes != null ? graceMinutes : 30
        );
        return Result.success("时段设置成功");
    }
    
    public Result getMedicationLogs(Long userId, String date) {
        try {
            String dateCondition = date != null ? 
                "AND DATE(ml.taken_time) = ?" : 
                "AND DATE(ml.taken_time) = CURDATE()";
            
            String sql = "SELECT ml.*, um.custom_name as medicine_name, s.slot_name " +
                "FROM medication_logs ml " +
                "LEFT JOIN user_medicines um ON ml.user_medicine_id = um.id " +
                "LEFT JOIN medicine_box_slot s ON ml.slot_id = s.id " +
                "WHERE um.user_id = ? AND um.is_deleted = 0 " + dateCondition + " " +
                "ORDER BY ml.taken_time DESC";
            
            List<Map<String, Object>> logs;
            if (date != null) {
                logs = jdbcTemplate.queryForList(sql, userId, date);
            } else {
                logs = jdbcTemplate.queryForList(sql, userId);
            }
            
            int normalCount = 0;
            int underdoseCount = 0;
            int overdoseCount = 0;
            
            for (Map<String, Object> log : logs) {
                String takeResult = (String) log.get("take_result");
                if ("normal".equals(takeResult)) {
                    normalCount++;
                } else if ("underdose".equals(takeResult)) {
                    underdoseCount++;
                } else if ("overdose".equals(takeResult)) {
                    overdoseCount++;
                }
            }
            
            Map<String, Object> result = new HashMap<>();
            result.put("logs", logs);
            result.put("summary", Map.of(
                "total", logs.size(),
                "normal", normalCount,
                "underdose", underdoseCount,
                "overdose", overdoseCount
            ));
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取服药记录失败", e);
            return Result.error("获取服药记录失败: " + e.getMessage());
        }
    }

    @Scheduled(fixedRate = 300000)
    public void checkMissedMedications() {
        log.info("[定时任务] 开始检查漏服药品...");
        
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime threshold = now.minusMinutes(60);
            
            String sql = "SELECT ml.id, ml.user_medicine_id, ml.scheduled_time, ml.status, " +
                        "um.user_id, um.custom_name, um.dose_unit, um.reminder_times, u.nickname as elder_name " +
                        "FROM medication_logs ml " +
                        "LEFT JOIN user_medicines um ON ml.user_medicine_id = um.id " +
                        "LEFT JOIN users u ON um.user_id = u.id " +
                        "WHERE ml.status = 'pending' " +
                        "AND ml.scheduled_time < ? " +
                        "AND DATE(ml.scheduled_time) = CURDATE() " +
                        "AND (um.is_deleted IS NULL OR um.is_deleted = 0)";
            
            List<Map<String, Object>> missedLogs = jdbcTemplate.queryForList(sql, threshold);
            
            for (Map<String, Object> record : missedLogs) {
                Long logId = ((Number) record.get("id")).longValue();
                Integer userMedicineId = ((Number) record.get("user_medicine_id")).intValue();
                Long userId = ((Number) record.get("user_id")).longValue();
                String medicineName = (String) record.get("custom_name");
                String elderName = (String) record.get("elder_name");
                String reminderTimesJson = (String) record.get("reminder_times");
                
                if (reminderTimesJson != null && !canStillMakeup(reminderTimesJson, now.toLocalTime())) {
                    jdbcTemplate.update(
                        "UPDATE medication_logs SET status = 'missed', is_read = 0 WHERE id = ?",
                        logId
                    );
                    
                    createMissedAlert(userMedicineId, medicineName, elderName);
                    notifyFamilyMembers(userId, "missed", medicineName, elderName, record.get("scheduled_time"));
                    
                    log.info("[漏服检测] 用户: {}, 药品: {}, 计划时间: {}", elderName, medicineName, record.get("scheduled_time"));
                } else {
                    log.info("[补服窗口] 用户: {}, 药品: {}, 仍在补服窗口内，暂不标记漏服", elderName, medicineName);
                }
            }
            
            log.info("[定时任务] 漏服检查完成，共检测到 {} 条待处理记录", missedLogs.size());
        } catch (Exception e) {
            log.error("[定时任务] 漏服检查失败", e);
        }
    }

    private boolean canStillMakeup(String reminderTimesJson, LocalTime currentTime) {
        try {
            com.alibaba.fastjson2.JSONArray times = com.alibaba.fastjson2.JSON.parseArray(reminderTimesJson);
            if (times == null || times.isEmpty()) {
                return false;
            }
            
            java.util.List<LocalTime> sortedTimes = new java.util.ArrayList<>();
            for (int i = 0; i < times.size(); i++) {
                String timeStr = times.getString(i);
                try {
                    sortedTimes.add(LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")));
                } catch (Exception e) {
                }
            }
            Collections.sort(sortedTimes);
            
            LocalTime nextReminderTime = null;
            for (LocalTime time : sortedTimes) {
                if (time.isAfter(currentTime)) {
                    nextReminderTime = time;
                    break;
                }
            }
            
            if (nextReminderTime == null) {
                nextReminderTime = sortedTimes.get(0);
            }
            
            long minutesToNext;
            if (nextReminderTime.isAfter(currentTime)) {
                minutesToNext = java.time.Duration.between(currentTime, nextReminderTime).toMinutes();
            } else {
                minutesToNext = java.time.Duration.between(currentTime, nextReminderTime.plusHours(24)).toMinutes();
            }
            
            return minutesToNext >= 120;
        } catch (Exception e) {
            log.error("解析提醒时间失败", e);
            return false;
        }
    }

    @Scheduled(fixedRate = 300000)
    public void checkOverdoseMedications() {
        log.info("[定时任务] 开始检查多服药品...");
        
        try {
            String sql = "SELECT ml.id, ml.user_medicine_id, ml.take_result, ml.taken_time, " +
                        "ml.weight_diff, ml.expected_dosage, um.user_id, um.custom_name, um.dose_unit, " +
                        "u.nickname as elder_name " +
                        "FROM medication_logs ml " +
                        "LEFT JOIN user_medicines um ON ml.user_medicine_id = um.id " +
                        "LEFT JOIN users u ON um.user_id = u.id " +
                        "WHERE ml.take_result = 'overdose' " +
                        "AND ml.status = 'taken' " +
                        "AND DATE(ml.taken_time) = CURDATE() " +
                        "AND (ml.overdose_notified IS NULL OR ml.overdose_notified = 0) " +
                        "AND (um.is_deleted IS NULL OR um.is_deleted = 0)";
            
            List<Map<String, Object>> overdoseLogs = jdbcTemplate.queryForList(sql);
            
            for (Map<String, Object> record : overdoseLogs) {
                Long logId = ((Number) record.get("id")).longValue();
                Integer userMedicineId = ((Number) record.get("user_medicine_id")).intValue();
                Long userId = ((Number) record.get("user_id")).longValue();
                String medicineName = (String) record.get("custom_name");
                String elderName = (String) record.get("elder_name");
                
                jdbcTemplate.update(
                    "UPDATE medication_logs SET take_result = 'overdose', is_read = 0, overdose_notified = 1 WHERE id = ?",
                    logId
                );
                
                createOverdoseAlert(userMedicineId, medicineName, elderName, record);
                notifyFamilyMembers(userId, "overdose", medicineName, elderName, record.get("taken_time"));
                
                log.info("[多服检测] 用户: {}, 药品: {}, 服药时间: {}", elderName, medicineName, record.get("taken_time"));
            }
            
            log.info("[定时任务] 多服检查完成，共检测到 {} 条多服记录", overdoseLogs.size());
        } catch (Exception e) {
            log.error("[定时任务] 多服检查失败", e);
        }
    }

    private void createMissedAlert(Integer userMedicineId, String medicineName, String elderName) {
        try {
            jdbcTemplate.update(
                "INSERT INTO medicine_stock_alert (user_medicine_id, alert_type, alert_level, message, is_read, is_resolved) " +
                "VALUES (?, 'missed', 3, ?, 0, 0)",
                userMedicineId, elderName + " 漏服了 " + medicineName + "，请及时关注"
            );
        } catch (Exception e) {
            log.error("创建漏服预警失败", e);
        }
    }

    private void createOverdoseAlert(Integer userMedicineId, String medicineName, String elderName, Map<String, Object> record) {
        try {
            Object weightDiff = record.get("weight_diff");
            Object expectedDosage = record.get("expected_dosage");
            String message = elderName + " 多服了 " + medicineName + 
                "（实际: " + weightDiff + ", 预期: " + expectedDosage + "），请及时关注";
            
            jdbcTemplate.update(
                "INSERT INTO medicine_stock_alert (user_medicine_id, alert_type, alert_level, message, is_read, is_resolved) " +
                "VALUES (?, 'overdose', 3, ?, 0, 0)",
                userMedicineId, message
            );
        } catch (Exception e) {
            log.error("创建多服预警失败", e);
        }
    }

    private void notifyFamilyMembers(Long elderId, String anomalyType, String medicineName, String elderName, Object time) {
        try {
            List<Map<String, Object>> familyMembers = jdbcTemplate.queryForList(
                "SELECT user_id FROM friendships WHERE friend_id = ? AND status = 1",
                elderId
            );
            
            for (Map<String, Object> family : familyMembers) {
                Long familyUserId = ((Number) family.get("user_id")).longValue();
                
                Map<String, Object> notification = new HashMap<>();
                notification.put("type", "MEDICATION_ANOMALY");
                notification.put("elderId", elderId);
                notification.put("elderName", elderName);
                notification.put("anomalyType", anomalyType);
                notification.put("medicineName", medicineName);
                notification.put("time", time != null ? time.toString() : "");
                notification.put("timestamp", System.currentTimeMillis());
                
                String content = anomalyType.equals("missed") ?
                    elderName + " 漏服了 " + medicineName :
                    elderName + " 多服了 " + medicineName;
                notification.put("content", content);
                
                jdbcTemplate.update(
                    "INSERT INTO notifications (user_id, type, title, content, data, is_read, create_time) " +
                    "VALUES (?, 'MEDICATION_ANOMALY', ?, ?, ?, 0, NOW())",
                    familyUserId, "服药异常提醒", content, objectMapper.writeValueAsString(notification)
                );
            }
            
            log.info("[通知] 已通知 {} 位家属关于 {} 的{}情况", familyMembers.size(), elderName, 
                anomalyType.equals("missed") ? "漏服" : "多服");
        } catch (Exception e) {
            log.error("通知家属失败", e);
        }
    }

    private List<String> parseReminderTimes(String reminderTimesJson) {
        List<String> result = new ArrayList<>();
        if (reminderTimesJson == null || reminderTimesJson.isEmpty()) {
            result = Arrays.asList("08:00", "18:00");
        } else {
            try {
                com.alibaba.fastjson2.JSONArray times = com.alibaba.fastjson2.JSON.parseArray(reminderTimesJson);
                if (times != null) {
                    for (int i = 0; i < times.size(); i++) {
                        result.add(times.getString(i));
                    }
                }
            } catch (Exception e) {
                result = Arrays.asList("08:00", "18:00");
            }
        }
        return result;
    }

    private int calculateDosingIntervalMinutes(List<String> reminderTimes) {
        if (reminderTimes == null || reminderTimes.size() <= 1) {
            return 720;
        }
        
        List<LocalTime> times = new ArrayList<>();
        for (String timeStr : reminderTimes) {
            try {
                times.add(LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")));
            } catch (Exception e) {
            }
        }
        Collections.sort(times);
        
        if (times.size() <= 1) {
            return 720;
        }
        
        int minInterval = Integer.MAX_VALUE;
        for (int i = 1; i < times.size(); i++) {
            long minutes = java.time.Duration.between(times.get(i - 1), times.get(i)).toMinutes();
            if (minutes > 0 && minutes < minInterval) {
                minInterval = (int) minutes;
            }
        }
        
        if (times.size() >= 2) {
            long crossDayInterval = (24 * 60 - times.get(times.size() - 1).toSecondOfDay() / 60) + times.get(0).toSecondOfDay() / 60;
            if (crossDayInterval > 0 && crossDayInterval < minInterval) {
                minInterval = (int) crossDayInterval;
            }
        }
        
        return minInterval;
    }

    private LocalTime calculateMakeupDeadTime(LocalTime plannedTime, int dosingIntervalMinutes, boolean isLastDose) {
        int makeupMinutes;
        if (isLastDose) {
            // 最后一次服药，补服截止时间设为12小时后，但绝对不允许跨天
            LocalTime endOfDay = LocalTime.of(23, 59, 59);
            // 计算从计划时间到当天结束有多少分钟
            long minutesToEndOfDay = java.time.Duration.between(plannedTime, endOfDay).toMinutes();
            
            // 如果从计划时间到当天结束的时间超过12小时，就取12小时
            // 否则就取当天结束时间
            if (minutesToEndOfDay >= 12 * 60) {
                makeupMinutes = 12 * 60;
            } else {
                makeupMinutes = (int) minutesToEndOfDay;
            }
        } else {
            // 非最后一次服药，使用原来的逻辑：服药间隔的一半，最少2小时
            makeupMinutes = Math.max(dosingIntervalMinutes / 2, 2 * 60);
        }
        return plannedTime.plusMinutes(makeupMinutes);
    }

    private LocalTime calculateEarlyAllowTime(LocalTime plannedTime, int dosingIntervalMinutes) {
        int earlyMinutes = 30;
        return plannedTime.minusMinutes(earlyMinutes);
    }

    public PlannedDoseInfo getPlannedDoseInfo(List<String> reminderTimes, LocalTime currentTime) {
        if (reminderTimes == null || reminderTimes.isEmpty()) {
            reminderTimes = Arrays.asList("08:00", "18:00");
        }
        
        List<LocalTime> sortedTimes = new ArrayList<>();
        for (String timeStr : reminderTimes) {
            try {
                sortedTimes.add(LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")));
            } catch (Exception e) {
            }
        }
        Collections.sort(sortedTimes);
        
        if (sortedTimes.isEmpty()) {
            sortedTimes.add(LocalTime.of(8, 0));
        }
        
        int dosingInterval = calculateDosingIntervalMinutes(reminderTimes);
        
        LocalTime plannedTime = null;
        LocalTime earlyAllowTime = null;
        LocalTime makeupDeadTime = null;
        boolean isFirstDose = false;
        boolean isLastDose = false;
        
        LocalTime firstTime = sortedTimes.get(0);
        LocalTime lastTime = sortedTimes.get(sortedTimes.size() - 1);
        
        int lastIndex = -1;
        for (int i = sortedTimes.size() - 1; i >= 0; i--) {
            if (!sortedTimes.get(i).isAfter(currentTime)) {
                plannedTime = sortedTimes.get(i);
                lastIndex = i;
                isFirstDose = (i == 0);
                break;
            }
        }
        
        if (plannedTime == null) {
            plannedTime = firstTime;
            isFirstDose = true;
            earlyAllowTime = calculateEarlyAllowTime(plannedTime, dosingInterval);
            makeupDeadTime = calculateMakeupDeadTime(plannedTime, dosingInterval, false);
            
            log.info("[第一次服药-调试] plannedTime={}, earlyAllowTime={}, dosingInterval={}", 
                plannedTime, earlyAllowTime, dosingInterval);
            
            PlannedDoseInfo info = new PlannedDoseInfo();
            info.plannedTime = plannedTime;
            info.earlyAllowTime = earlyAllowTime;
            info.makeupDeadTime = makeupDeadTime;
            info.dosingIntervalMinutes = dosingInterval;
            info.isFirstDose = true;
            info.isLastDose = false;
            
            log.info("[第一次服药-返回] info.earlyAllowTime={}", info.earlyAllowTime);
            
            return info;
        }
        
        // 关键修复：检查是否已经错过上次服药的补服窗口
        // 如果错过了，检查是否已经接近下次服药的提前服药窗口
        LocalTime lastMakeupDeadTime = calculateMakeupDeadTime(plannedTime, dosingInterval, plannedTime.equals(lastTime));
        if (currentTime.isAfter(lastMakeupDeadTime) || currentTime.equals(lastMakeupDeadTime)) {
            // 已经错过补服窗口，检查是否有下一次服药
            int nextIndex = lastIndex + 1;
            if (nextIndex < sortedTimes.size()) {
                // 有下一次服药，检查是否已经可以提前服用
                LocalTime nextPlannedTime = sortedTimes.get(nextIndex);
                LocalTime nextEarlyAllowTime = calculateEarlyAllowTime(nextPlannedTime, dosingInterval);
                
                if (!currentTime.isBefore(nextEarlyAllowTime)) {
                    // 已经进入下次服药的提前服药窗口，切换到下次服药
                    plannedTime = nextPlannedTime;
                    isFirstDose = (nextIndex == 0);
                    isLastDose = (nextIndex == sortedTimes.size() - 1);
                    earlyAllowTime = nextEarlyAllowTime;
                    makeupDeadTime = calculateMakeupDeadTime(plannedTime, dosingInterval, isLastDose);
                    
                    PlannedDoseInfo info = new PlannedDoseInfo();
                    info.plannedTime = plannedTime;
                    info.earlyAllowTime = earlyAllowTime;
                    info.makeupDeadTime = makeupDeadTime;
                    info.dosingIntervalMinutes = dosingInterval;
                    info.isFirstDose = isFirstDose;
                    info.isLastDose = isLastDose;
                    
                    return info;
                }
            }
        }
        
        isLastDose = plannedTime.equals(lastTime);
        
        earlyAllowTime = calculateEarlyAllowTime(plannedTime, dosingInterval);
        makeupDeadTime = calculateMakeupDeadTime(plannedTime, dosingInterval, isLastDose);
        
        PlannedDoseInfo info = new PlannedDoseInfo();
        info.plannedTime = plannedTime;
        info.earlyAllowTime = earlyAllowTime;
        info.makeupDeadTime = makeupDeadTime;
        info.dosingIntervalMinutes = dosingInterval;
        info.isFirstDose = isFirstDose;
        info.isLastDose = isLastDose;
        
        return info;
    }

    public static class PlannedDoseInfo {
        LocalTime plannedTime;
        LocalTime earlyAllowTime;
        LocalTime makeupDeadTime;
        int dosingIntervalMinutes;
        boolean isFirstDose;
        boolean isLastDose;
    }

    private MedicationAction judgeMedicationAction(LocalTime actualTime, PlannedDoseInfo doseInfo) {
        MedicationAction action = new MedicationAction();
        
        if (doseInfo.isFirstDose && actualTime.isBefore(doseInfo.plannedTime)) {
            action.type = "too_early";
            action.message = "首次用药请在计划时间 " + doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " 服用";
            action.detailMessage = "首次用药前体内没有药物残留，提前服药可能导致血药浓度过高，请在计划时间准时服用。";
            return action;
        }
        
        if (doseInfo.earlyAllowTime.equals(doseInfo.plannedTime) && actualTime.isBefore(doseInfo.plannedTime)) {
            action.type = "too_early";
            action.message = "距离计划服药时间还太早，请在 " + doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " 准时服用";
            action.detailMessage = "当前不在服药时间窗口内，请在计划时间 " + doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " 准时服用。";
            return action;
        }
        
        if (actualTime.isBefore(doseInfo.earlyAllowTime)) {
            action.type = "too_early";
            action.message = "距离计划服药时间还太早，请在 " + doseInfo.earlyAllowTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " 之后服用";
            action.detailMessage = "过早服药可能导致药物在体内浓度过高，建议在计划时间前 " + (doseInfo.dosingIntervalMinutes / 4) + " 分钟内服用。";
            return action;
        }
        
        if (actualTime.isBefore(doseInfo.plannedTime)) {
            action.type = "early";
            action.message = "提前服药成功，下次服药时间不变";
            action.detailMessage = "您在计划时间前服药，下次服药请按原计划时间 " + 
                doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " 服用。";
            return action;
        }
        
        if (actualTime.isBefore(doseInfo.makeupDeadTime) || actualTime.equals(doseInfo.makeupDeadTime)) {
            long minutesSincePlanned = java.time.Duration.between(doseInfo.plannedTime, actualTime).toMinutes();
            
            if (minutesSincePlanned <= 30) {
                action.type = "normal";
                action.message = "服药成功";
                action.detailMessage = "您已按时服药，下次服药请按原计划时间进行。";
            } else {
                action.type = "makeup";
                action.message = "补服成功，下次服药时间不变";
                action.detailMessage = "您已成功补服，下次服药请按原计划时间 " + 
                    doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")) + " 进行。补服截止时间为 " +
                    doseInfo.makeupDeadTime.format(DateTimeFormatter.ofPattern("HH:mm")) + "。";
            }
            return action;
        }
        
        action.type = "missed";
        action.message = "已错过补服时间，请跳过本次，等待下次服药";
        action.detailMessage = "您已错过本次补服窗口（截止时间 " + 
            doseInfo.makeupDeadTime.format(DateTimeFormatter.ofPattern("HH:mm")) + "），为避免药物过量，请跳过本次服药，等待下次计划时间。";
        return action;
    }

    private static class MedicationAction {
        String type;
        String message;
        String detailMessage;
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
    
    public Result getDeviceBinding(Long userId, String deviceId) {
        try {
            MedicineBox box = medicineBoxMapper.selectByUserId(userId);
            if (box == null) {
                return Result.success(null);
            }
            
            List<Map<String, Object>> slots = jdbcTemplate.queryForList(
                "SELECT s.id, s.slot_number, s.user_medicine_id, s.dosage_weight, um.custom_name, um.dosage_per_time, um.dose_unit " +
                "FROM medicine_box_slot s " +
                "LEFT JOIN user_medicines um ON s.user_medicine_id = um.id " +
                "WHERE s.box_id = ? AND s.user_medicine_id IS NOT NULL",
                box.getId()
            );
            
            if (slots.isEmpty()) {
                return Result.success(null);
            }
            
            Map<String, Object> slot = slots.get(0);
            Map<String, Object> result = new HashMap<>();
            result.put("medicineId", slot.get("user_medicine_id"));
            result.put("medicineName", slot.get("custom_name"));
            result.put("singleDoseWeight", slot.get("dosage_weight"));
            result.put("dosagePerTime", slot.get("dosage_per_time"));
            result.put("unit", slot.get("dose_unit"));
            
            boolean hasDosageWeight = slot.get("dosage_weight") != null;
            boolean isInitialized = (box.getInitialized() != null && box.getInitialized()) || hasDosageWeight;
            result.put("initStep", isInitialized ? 3 : 1);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("获取设备绑定信息失败", e);
            return Result.error("获取设备绑定信息失败: " + e.getMessage());
        }
    }
    
    public Result bindDevice(Long userId, String deviceId, Long medicineId, String medicineName, Integer initStep, Double singleDoseWeight) {
        try {
            MedicineBox box = medicineBoxMapper.selectByUserId(userId);
            if (box == null) {
                box = createDefaultBox(userId, deviceId);
            }
            
            if (initStep != null && initStep == 1) {
                box.setInitialized(false);
                box.setSingleDoseWeight(null);
                box.setFullBottleWeight(null);
                box.setToleranceMin(null);
                box.setToleranceMax(null);
                box.setSecondWeighingWeight(null);
                box.setInitFirstWeight(null);
                box.setInitStep(null);
                box.setIsLocked(false);
                box.setLockedUntil(null);
                box.setDeviceId(deviceId);
                medicineBoxMapper.update(box);
                
                jdbcTemplate.update(
                    "UPDATE medicine_box SET init_step = NULL, full_bottle_weight = NULL, " +
                    "second_weighing_weight = NULL, init_first_weight = NULL, " +
                    "tolerance_min = NULL, tolerance_max = NULL, update_time = NOW() WHERE id = ?",
                    box.getId()
                );
                
                jdbcTemplate.update(
                    "UPDATE medicine_box_slot SET user_medicine_id = NULL, slot_name = NULL, " +
                    "dosage_weight = NULL, current_weight = 0, weight_before_take = NULL, " +
                    "weight_after_take = NULL, take_status = 'pending', last_take_time = NULL, " +
                    "bind_time_slot = NULL, update_time = NOW() WHERE box_id = ?",
                    box.getId()
                );
                
                log.info("[设备绑定-初始化] userId={}, deviceId={}, 重置药盒状态", userId, deviceId);
            }
            
            if (initStep != null && initStep >= 3) {
                box.setInitialized(true);
                box.setSingleDoseWeight(singleDoseWeight != null ? new BigDecimal(singleDoseWeight) : null);
                medicineBoxMapper.update(box);
            }
            
            if (medicineId != null) {
                List<Map<String, Object>> slots = jdbcTemplate.queryForList(
                    "SELECT id FROM medicine_box_slot WHERE box_id = ? ORDER BY slot_number LIMIT 1",
                    box.getId()
                );
                
                if (!slots.isEmpty()) {
                    Long slotId = ((Number) slots.get(0).get("id")).longValue();
                    jdbcTemplate.update(
                        "UPDATE medicine_box_slot SET user_medicine_id = ?, slot_name = ?, dosage_weight = ?, update_time = NOW() WHERE id = ?",
                        medicineId, medicineName, singleDoseWeight, slotId
                    );
                }
            }
            
            log.info("[设备绑定] userId={}, deviceId={}, medicineId={}, initStep={}", userId, deviceId, medicineId, initStep);
            return Result.success("绑定成功");
        } catch (Exception e) {
            log.error("设备绑定失败", e);
            return Result.error("设备绑定失败: " + e.getMessage());
        }
    }
    
    public Result unbindDevice(Long userId, String deviceId) {
        try {
            MedicineBox box = medicineBoxMapper.selectByUserId(userId);
            if (box == null) {
                return Result.success("解绑成功");
            }
            
            Long boxId = box.getId();
            
            List<Map<String, Object>> slots = jdbcTemplate.queryForList(
                "SELECT id, user_medicine_id FROM medicine_box_slot WHERE box_id = ?",
                boxId
            );
            
            for (Map<String, Object> slot : slots) {
                Integer slotId = ((Number) slot.get("id")).intValue();
                Object userMedicineId = slot.get("user_medicine_id");
                
                jdbcTemplate.update(
                    "DELETE FROM medication_logs WHERE slot_id = ?",
                    slotId
                );
                
                if (userMedicineId != null) {
                    jdbcTemplate.update(
                        "DELETE FROM medication_logs WHERE user_medicine_id = ? AND source = 'device'",
                        userMedicineId
                    );
                }
            }
            
            jdbcTemplate.update(
                "DELETE FROM box_events WHERE device_id = ?",
                box.getDeviceId()
            );
            
            jdbcTemplate.update(
                "UPDATE medicine_box_slot SET user_medicine_id = NULL, slot_name = NULL, " +
                "dosage_weight = NULL, current_weight = 0, weight_before_take = NULL, " +
                "weight_after_take = NULL, take_status = 'pending', last_take_time = NULL, " +
                "bind_time_slot = NULL, update_time = NOW() WHERE box_id = ?",
                boxId
            );
            
            box.setInitialized(false);
            box.setSingleDoseWeight(null);
            box.setFullBottleWeight(null);
            box.setToleranceMin(null);
            box.setToleranceMax(null);
            box.setSecondWeighingWeight(null);
            box.setInitFirstWeight(null);
            box.setInitStep(null);
            box.setIsLocked(false);
            box.setLockedUntil(null);
            box.setLastSyncTime(null);
            medicineBoxMapper.update(box);
            
            log.info("[设备解绑] userId={}, deviceId={}, 已清除所有智能药盒数据", userId, deviceId);
            return Result.success("解绑成功，已清除所有智能药盒数据");
        } catch (Exception e) {
            log.error("设备解绑失败", e);
            return Result.error("设备解绑失败: " + e.getMessage());
        }
    }
    
    public Result notifyFamily(Long userId, String message, String type) {
        try {
            log.info("[通知家属] 开始处理, userId={}, message={}, type={}", userId, message, type);
            
            String elderName = getElderName(userId);
            log.info("[通知家属] 长辈姓名: {}", elderName);
            
            List<Map<String, Object>> emergencyContacts = jdbcTemplate.queryForList(
                "SELECT contact_user_id, contact_phone FROM emergency_contacts WHERE user_id = ?",
                userId
            );
            
            log.info("[通知家属] 查询到 {} 个紧急联系人", emergencyContacts.size());
            
            if (emergencyContacts.isEmpty()) {
                log.info("[通知家属] userId={} 无紧急联系人", userId);
                return Result.success("无紧急联系人");
            }
            
            String fullMessage = String.format("【服药异常提醒】%s: %s", elderName, message);
            log.info("[通知家属] 完整消息: {}", fullMessage);
            
            int notifiedCount = 0;
            for (Map<String, Object> contact : emergencyContacts) {
                Long contactUserId = contact.get("contact_user_id") != null ? 
                    ((Number) contact.get("contact_user_id")).longValue() : null;
                String contactPhone = (String) contact.get("contact_phone");
                
                log.info("[通知家属] 处理联系人, contactUserId={}, contactPhone={}", contactUserId, contactPhone);
                
                // 二级预警：App推送通知
                if (contactUserId != null) {
                    log.info("[通知家属] 准备插入消息到messages表, fromId={}, toId={}", userId, contactUserId);
                    int rowsAffected = jdbcTemplate.update(
                        "INSERT INTO messages (from_id, to_id, content, msg_type, is_read, create_time) " +
                        "VALUES (?, ?, ?, ?, 0, NOW())",
                        userId, contactUserId, fullMessage, type
                    );
                    if (rowsAffected > 0) {
                        notifiedCount++;
                        log.info("[通知家属-二级预警] 成功插入消息, rowsAffected={}", rowsAffected);
                    } else {
                        log.warn("[通知家属-二级预警] 插入消息失败, rowsAffected=0");
                    }
                }
            }
            
            log.info("[通知家属] 完成, userId={}, message={}, 已通知{}位紧急联系人", userId, message, notifiedCount);
            return Result.success("已通知 " + notifiedCount + " 位紧急联系人");
        } catch (Exception e) {
            log.error("[通知家属] 异常", e);
            return Result.error("通知家属失败: " + e.getMessage());
        }
    }
    
    @Transactional
    public Result confirmRestock(Long userId, Long slotId, Long userMedicineId, Integer restockQuantity) {
        try {
            int quantity = restockQuantity != null && restockQuantity > 0 ? restockQuantity : 1;
            log.info("[确认放回药物] userId={}, slotId={}, userMedicineId={}, quantity={}", userId, slotId, userMedicineId, quantity);
            
            Map<String, Object> slot = getSlotById(slotId);
            if (slot == null) {
                return Result.error("药仓不存在");
            }
            
            BigDecimal dosageWeight = new BigDecimal("1.0");
            Object dosageObj = slot.get("dosage_weight");
            if (dosageObj != null) {
                dosageWeight = new BigDecimal(dosageObj.toString());
            }
            
            int currentActualTake = slot.get("actual_take_count") != null ? 
                ((Number) slot.get("actual_take_count")).intValue() : 0;
            BigDecimal currentWeight = slot.get("current_weight") != null ? 
                new BigDecimal(slot.get("current_weight").toString()) : BigDecimal.ZERO;
            
            int newActualTake = Math.max(0, currentActualTake - quantity);
            BigDecimal newWeight = currentWeight.add(dosageWeight.multiply(new BigDecimal(quantity)));
            
            jdbcTemplate.update(
                "UPDATE medicine_box_slot SET actual_take_count = ?, current_weight = ?, update_time = NOW() WHERE id = ?",
                newActualTake, newWeight, slotId
            );
            
            if (userMedicineId != null) {
                jdbcTemplate.update(
                    "UPDATE user_medicines SET current_stock = current_stock + ?, update_time = NOW() WHERE id = ?",
                    quantity, userMedicineId
                );
            }
            
            BigDecimal dosagePerTime = getDosagePerTime(userMedicineId != null ? userMedicineId.intValue() : null);
            int expectedDosage = dosagePerTime != null ? dosagePerTime.intValue() : 1;
            
            String cumulativeResult = judgeCumulativeTakeResult(newActualTake, expectedDosage);
            
            Map<String, Object> result = new HashMap<>();
            result.put("slotId", slotId);
            result.put("userMedicineId", userMedicineId);
            result.put("restockedQuantity", quantity);
            result.put("newActualTake", newActualTake);
            result.put("expectedDosage", expectedDosage);
            result.put("cumulativeResult", cumulativeResult);
            
            String medicineName = (String) slot.get("slot_name");
            result.put("medicineName", medicineName);
            result.put("newWeight", newWeight);
            
            if ("underdose".equals(cumulativeResult)) {
                result.put("needMore", true);
                result.put("message", "放回成功，请继续取药");
                result.put("detailMessage", "您已取 " + newActualTake + " 粒，本次应服 " + expectedDosage + " 粒，请继续取药。");
                log.info("[放回后-少服] userId={}, 已取={}, 应取={}", userId, newActualTake, expectedDosage);
                return Result.success(result);
            }
            
            if ("overdose".equals(cumulativeResult)) {
                result.put("needRestock", true);
                result.put("message", "放回成功，请继续放回多余药品");
                result.put("detailMessage", "您已取 " + newActualTake + " 粒，本次应服 " + expectedDosage + " 粒，请继续放回 " + (newActualTake - expectedDosage) + " 粒。");
                log.info("[放回后-多服] userId={}, 已取={}, 应取={}", userId, newActualTake, expectedDosage);
                return Result.success(result);
            }
            
            PlannedDoseInfo doseInfo = null;
            if (userMedicineId != null) {
                String reminderTimesJson = getReminderTimes(userMedicineId.intValue());
                List<String> reminderTimes = parseReminderTimes(reminderTimesJson);
                doseInfo = getPlannedDoseInfo(reminderTimes, LocalTime.now());
            }
            
            completeMedicationSession(userId, userMedicineId, slotId, newActualTake, expectedDosage, null, null, null, "normal", doseInfo);
            
            result.put("takeSuccess", true);
            result.put("message", "服药成功");
            result.put("detailMessage", "您已正确服用 " + expectedDosage + " 粒药品。");
            
            if (doseInfo != null) {
                LocalTime nowTime = LocalTime.now();
                if (nowTime.isAfter(doseInfo.plannedTime.plusMinutes(30))) {
                    result.put("isMakeup", true);
                    result.put("message", "补服成功");
                } else if (nowTime.isBefore(doseInfo.plannedTime)) {
                    result.put("isEarly", true);
                    result.put("message", "提前服药成功");
                }
            }
            
            log.info("[放回后-正常] userId={}, 已取={}, 应取={}", userId, newActualTake, expectedDosage);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("确认放回药物失败", e);
            return Result.error("放回失败: " + e.getMessage());
        }
    }
    
    private Map<String, Object> createRestockPayload(Long slotId, BigDecimal restockedAmount, BigDecimal newWeight) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("slotId", slotId);
        payload.put("restockedAmount", restockedAmount);
        payload.put("newWeight", newWeight);
        payload.put("timestamp", System.currentTimeMillis());
        return payload;
    }
    
    @Transactional
    public Result resetMedicationSession(Long userId, Long slotId) {
        try {
            log.info("[重置服药会话] userId={}, slotId={}", userId, slotId);
            
            Map<String, Object> slot = getSlotById(slotId);
            if (slot == null) {
                return Result.error("药仓不存在");
            }
            
            int currentActualTake = slot.get("actual_take_count") != null ? 
                ((Number) slot.get("actual_take_count")).intValue() : 0;
            
            jdbcTemplate.update(
                "UPDATE medicine_box_slot SET actual_take_count = 0, take_match_status = 'pending', update_time = NOW() WHERE id = ?",
                slotId
            );
            
            Map<String, Object> result = new HashMap<>();
            result.put("resetSuccess", true);
            result.put("message", "服药会话已重置");
            result.put("previousTakeCount", currentActualTake);
            
            log.info("[重置服药会话成功] userId={}, slotId={}, previousTakeCount={}", userId, slotId, currentActualTake);
            
            return Result.success(result);
        } catch (Exception e) {
            log.error("重置服药会话失败", e);
            return Result.error("重置失败: " + e.getMessage());
        }
    }
    
    private String getNextReminderTime(String reminderTimes) {
        if (reminderTimes == null || reminderTimes.isEmpty()) return "08:00";
        
        List<String> times = parseReminderTimes(reminderTimes);
        if (times.isEmpty()) return "08:00";
        
        LocalTime now = LocalTime.now();
        for (String timeStr : times) {
            try {
                LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                if (time.isAfter(now)) {
                    return timeStr;
                }
            } catch (Exception e) {
            }
        }
        
        return times.get(0);
    }
}

package com.chat.older.xiechen.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.*;
import com.chat.older.xiechen.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class MedicineAssistantService {

    private static final Logger log = LoggerFactory.getLogger(MedicineAssistantService.class);

    @Autowired
    private UserMedicineMapper userMedicineMapper;
    @Autowired
    private MedicineInfoMapper medicineInfoMapper;
    @Autowired
    private MedicationLogMapper medicationLogMapper;
    @Autowired
    private MedicineStockAlertMapper stockAlertMapper;
    @Autowired
    private MedicineService medicineService;
    @Autowired
    private MedicineBoxMapper medicineBoxMapper;
    @Autowired
    private MedicineVoiceExtractor medicineVoiceExtractor;
    @Autowired
    private MedicineOperationLogMapper operationLogMapper;
    @Autowired
    private FriendMapper friendMapper;
    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private UserMapper userMapper;
    @Autowired
    private MedicineBoxService medicineBoxService;

    public List<Map<String, Object>> getUserMedicines(Integer userId) {
        List<UserMedicine> medicines = userMedicineMapper.selectByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        
        LocalDate today = LocalDate.now();
        LocalTime now = LocalTime.now();
        
        for (UserMedicine med : medicines) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", med.getId());
            item.put("name", med.getCustomName() != null ? med.getCustomName() : med.getMedicineName());
            item.put("type", med.getType());
            item.put("spec", med.getSpec());
            item.put("dosagePerTime", med.getDosagePerTime());
            item.put("unit", med.getDoseUnit());
            item.put("frequency", med.getFrequencyPerDay());
            item.put("totalStock", med.getTotalStock());
            item.put("currentStock", med.getCurrentStock());
            item.put("perPackage", med.getPackageQuantity() != null ? med.getPackageQuantity() : 30);
            
            int dailyUsage = med.getDosagePerTime().intValue() * med.getFrequencyPerDay();
            int daysLeft = dailyUsage > 0 ? med.getCurrentStock() / dailyUsage : 0;
            item.put("daysLeft", daysLeft);
            
            double stockPercent = med.getTotalStock() > 0 ? 
                (double) med.getCurrentStock() / med.getTotalStock() * 100 : 0;
            item.put("stockPercent", Math.round(stockPercent * 100.0) / 100.0);
            
            String nextReminder = getNextReminderTime(med.getReminderTimes());
            item.put("nextReminder", nextReminder);
            List<String> reminderTimesList = parseReminderTimes(med.getReminderTimes());
            item.put("reminderTimes", reminderTimesList);
            item.put("drugInstruction", med.getDrugInstruction());
            item.put("indication", med.getIndication());
            item.put("precautions", med.getPrecautions() != null ? med.getPrecautions() : med.getDrugInstruction());
            
            List<MedicationLog> todayLogs = medicationLogMapper.selectTodayByUserMedicineId(med.getId());
            
            Map<String, String> timeStatusMap = getTimeStatusMap(med.getId(), today, now, reminderTimesList, todayLogs);
            item.put("timeStatusMap", timeStatusMap);
            
            String takeStatus = getMedicineTakeStatus(med.getId(), today, now, reminderTimesList);
            item.put("takeStatus", takeStatus);
            
            long takenCount = todayLogs.stream().filter(log -> 
                "taken".equals(log.getStatus()) || 
                "taken_early".equals(log.getStatus()) || 
                "taken_makeup".equals(log.getStatus()) ||
                "taken_underdose".equals(log.getStatus()) ||
                "taken_overdose".equals(log.getStatus())
            ).count();
            item.put("takenCount", (int) takenCount);
            
            List<Map<String, Object>> takeRecords = new ArrayList<>();
            for (MedicationLog log : todayLogs) {
                Map<String, Object> recordMap = new HashMap<>();
                recordMap.put("takeTime", log.getTakenTime() != null ? 
                    log.getTakenTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
                recordMap.put("isEarly", "taken_early".equals(log.getStatus()));
                recordMap.put("isMakeup", "taken_makeup".equals(log.getStatus()));
                takeRecords.add(recordMap);
            }
            item.put("takeRecords", takeRecords);
            
            result.add(item);
        }
        return result;
    }
    
    private Map<String, String> getTimeStatusMap(Integer userMedicineId, LocalDate today, LocalTime now, 
                                                    List<String> reminderTimes, List<MedicationLog> todayLogs) {
        Map<String, String> timeStatusMap = new HashMap<>();
        
        if (reminderTimes == null || reminderTimes.isEmpty()) {
            return timeStatusMap;
        }
        
        List<LocalTime> sortedReminderTimes = new ArrayList<>();
        for (String timeStr : reminderTimes) {
            try {
                sortedReminderTimes.add(LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")));
            } catch (Exception e) {
            }
        }
        Collections.sort(sortedReminderTimes);
        
        if (sortedReminderTimes.isEmpty()) {
            return timeStatusMap;
        }
        
        Set<Long> usedLogIds = new HashSet<>();
        
        for (LocalTime reminderTime : sortedReminderTimes) {
            String timeKey = reminderTime.format(DateTimeFormatter.ofPattern("HH:mm"));
            
            boolean hasTaken = false;
            MedicationLog matchedLog = null;
            long minDiff = Long.MAX_VALUE;

            MedicineBoxService.PlannedDoseInfo doseInfo = medicineBoxService.getPlannedDoseInfo(
                reminderTimes, reminderTime);
            
            for (MedicationLog log : todayLogs) {
                if (usedLogIds.contains(log.getId())) {
                    continue;
                }
                
                String status = log.getStatus();
                if (("taken".equals(status) || "taken_early".equals(status) || "taken_makeup".equals(status) ||
                        "taken_underdose".equals(status) || "taken_overdose".equals(status)) 
                        && log.getTakenTime() != null) {
                    LocalTime takenTime = log.getTakenTime().toLocalTime();
                    if (!takenTime.isBefore(doseInfo.earlyAllowTime) && 
                        !takenTime.isAfter(doseInfo.makeupDeadTime)) {
                        long absDiff = Math.abs(java.time.Duration.between(reminderTime, takenTime).toMinutes());
                        if (absDiff < minDiff) {
                            minDiff = absDiff;
                            matchedLog = log;
                        }
                    }
                }
            }
            
            if (matchedLog != null) {
                hasTaken = true;
                usedLogIds.add(matchedLog.getId());
            }
            
            if (hasTaken) {
                timeStatusMap.put(timeKey, "taken");
            } else {
                MedicineBoxService.PlannedDoseInfo currentDoseInfo = medicineBoxService.getPlannedDoseInfo(
                    reminderTimes, now);
                if (currentDoseInfo.plannedTime.equals(reminderTime)) {
                    if (now.isAfter(currentDoseInfo.makeupDeadTime) || now.equals(currentDoseInfo.makeupDeadTime)) {
                        timeStatusMap.put(timeKey, "overdue");
                    } else {
                        timeStatusMap.put(timeKey, "pending");
                    }
                } else {
                    MedicineBoxService.PlannedDoseInfo thisDoseInfo = medicineBoxService.getPlannedDoseInfo(
                        reminderTimes, reminderTime);
                    
                    if (now.isAfter(thisDoseInfo.makeupDeadTime) || now.equals(thisDoseInfo.makeupDeadTime)) {
                        timeStatusMap.put(timeKey, "overdue");
                    } else {
                        timeStatusMap.put(timeKey, "pending");
                    }
                }
            }
        }
        
        return timeStatusMap;
    }
    
    private String getMedicineTakeStatus(Integer userMedicineId, LocalDate today, LocalTime now, List<String> reminderTimes) {
        if (reminderTimes == null || reminderTimes.isEmpty()) {
            reminderTimes = Arrays.asList("08:00", "18:00");
        }
        
        List<MedicationLog> todayLogs = medicationLogMapper.selectTodayByUserMedicineId(userMedicineId);
        MedicineBoxService.PlannedDoseInfo doseInfo = medicineBoxService.getPlannedDoseInfo(reminderTimes, now);
        
        boolean hasTakenThisTimeSlot = false;
        String takeResultType = null;
        
        for (MedicationLog log : todayLogs) {
            String status = log.getStatus();
            if (("taken".equals(status) || "taken_early".equals(status) || "taken_makeup".equals(status) ||
                    "taken_underdose".equals(status) || "taken_overdose".equals(status)) && log.getTakenTime() != null) {
                LocalTime takenTime = log.getTakenTime().toLocalTime();
                if (!takenTime.isBefore(doseInfo.earlyAllowTime) && 
                    !takenTime.isAfter(doseInfo.makeupDeadTime)) {
                    hasTakenThisTimeSlot = true;
                    if ("taken_underdose".equals(status)) {
                        takeResultType = "underdose";
                    } else if ("taken_overdose".equals(status)) {
                        takeResultType = "overdose";
                    } else {
                        takeResultType = "taken";
                    }
                    break;
                }
            }
        }
        
        if (hasTakenThisTimeSlot) {
            return takeResultType != null ? takeResultType : "taken";
        }
        if (now.isAfter(doseInfo.makeupDeadTime) || now.equals(doseInfo.makeupDeadTime)) {
            return "overdue";
        }
        
        return "pending";
    }

    public Map<String, Object> getMedicineDetail(Integer medicineId) {
        UserMedicine med = userMedicineMapper.selectById(medicineId);
        if (med == null) return null;
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", med.getId());
        result.put("name", med.getCustomName() != null ? med.getCustomName() : med.getMedicineName());
        result.put("type", med.getType());
        result.put("spec", med.getSpec());
        result.put("dosagePerTime", med.getDosagePerTime());
        result.put("unit", med.getDoseUnit());
        result.put("frequency", med.getFrequencyPerDay());
        result.put("totalStock", med.getTotalStock());
        result.put("currentStock", med.getCurrentStock());
        result.put("perPackage", med.getPackageQuantity() != null ? med.getPackageQuantity() : 30);
        result.put("drugInstruction", med.getDrugInstruction());
        result.put("indication", med.getIndication());
        result.put("precautions", med.getPrecautions() != null ? med.getPrecautions() : med.getDrugInstruction());
        result.put("reminderTimes", parseReminderTimes(med.getReminderTimes()));
        
        return result;
    }

    @Transactional
    public Result addMedicine(Integer userId, Map<String, Object> params) {
        UserMedicine medicine = new UserMedicine();
        medicine.setUserId(userId);
        
        String name = (String) params.get("name");
        if (name == null || name.isEmpty()) {
            name = (String) params.get("medicineName");
        }
        if (name == null || name.isEmpty()) {
            return Result.error("药品名称不能为空");
        }
        
        MedicineInfo info = medicineInfoMapper.selectByName(name);
        
        if (info != null) {
            medicine.setMedicineId(info.getId());
            medicine.setType(info.getType());
        } else {
            medicine.setCustomName(name);
            medicine.setType((String) params.getOrDefault("type", "其他"));
        }
        
        medicine.setSpec((String) params.get("spec"));
        medicine.setDoseUnit((String) params.getOrDefault("unit", "片"));
        medicine.setPackageUnit((String) params.getOrDefault("packageUnit", "盒"));
        
        Object dosageObj = params.get("dosagePerTime");
        BigDecimal dosage = dosageObj instanceof Number ? 
            new BigDecimal(dosageObj.toString()) : new BigDecimal("1");
        medicine.setDosagePerTime(dosage);
        
        Object freqObj = params.get("frequency");
        Integer frequency = freqObj instanceof Number ? 
            ((Number) freqObj).intValue() : 2;
        medicine.setFrequencyPerDay(frequency);
        
        Object packagesObj = params.get("packages");
        Integer packages = packagesObj instanceof Number ? 
            ((Number) packagesObj).intValue() : 1;
        medicine.setTotalPackages(packages);
        
        Object perPackageObj = params.get("perPackage");
        if (perPackageObj == null) {
            perPackageObj = params.get("pillsPerBox");
        }
        Integer perPackage = perPackageObj instanceof Number ? 
            ((Number) perPackageObj).intValue() : 30;
        medicine.setPackageQuantity(perPackage);
        
        int totalStock = packages * perPackage;
        medicine.setTotalStock(totalStock);
        medicine.setCurrentStock(totalStock);
        medicine.setThresholdDays(2);
        medicine.setStatus(1);
        medicine.setStartDate(LocalDate.now());
        
        List<String> reminderTimes = (List<String>) params.get("reminderTimes");
        if (reminderTimes != null && !reminderTimes.isEmpty()) {
            medicine.setReminderTimes(JSON.toJSONString(reminderTimes));
        } else {
            medicine.setReminderTimes("[\"08:00\",\"18:00\"]");
        }
        
        if (params.containsKey("precautions")) {
            medicine.setPrecautions((String) params.get("precautions"));
        }
        
        userMedicineMapper.insert(medicine);
        
        generateMedicationLogs(medicine.getId(), reminderTimes != null ? reminderTimes : Arrays.asList("08:00", "18:00"));
        
        return Result.success(medicine);
    }

    @Transactional
    public Result updateMedicine(Integer userId, Integer medicineId, Map<String, Object> params) {
        UserMedicine medicine = userMedicineMapper.selectByIdIncludeDeleted(medicineId);
        if (medicine == null) {
            return Result.error("药品不存在");
        }
        
        if (!canOperate(userId.longValue(), medicine.getUserId().longValue())) {
            return Result.error("无权操作该药品");
        }
        
        if (medicine.getIsDeleted() != null && medicine.getIsDeleted() == 1) {
            return Result.error("该药品已被删除");
        }
        
        String beforeData = JSON.toJSONString(medicine);
        
        if (params.containsKey("name")) {
            String name = (String) params.get("name");
            if (name == null || name.isEmpty()) {
                name = (String) params.get("medicineName");
            }
            if (name != null && !name.isEmpty()) {
                MedicineInfo info = medicineInfoMapper.selectByName(name);
                if (info != null) {
                    medicine.setMedicineId(info.getId());
                    medicine.setType(info.getType());
                    medicine.setCustomName(null);
                } else {
                    medicine.setCustomName(name);
                }
            }
        }
        
        if (params.containsKey("type")) {
            medicine.setType((String) params.get("type"));
        }
        
        if (params.containsKey("spec")) medicine.setSpec((String) params.get("spec"));
        if (params.containsKey("unit")) medicine.setDoseUnit((String) params.get("unit"));
        if (params.containsKey("dosagePerTime")) {
            Object obj = params.get("dosagePerTime");
            medicine.setDosagePerTime(obj instanceof Number ? new BigDecimal(obj.toString()) : new BigDecimal("1"));
        }
        if (params.containsKey("frequency")) {
            Object obj = params.get("frequency");
            medicine.setFrequencyPerDay(obj instanceof Number ? ((Number) obj).intValue() : 2);
        }
        if (params.containsKey("currentStock")) {
            Object obj = params.get("currentStock");
            medicine.setCurrentStock(obj instanceof Number ? ((Number) obj).intValue() : 0);
        }
        if (params.containsKey("perPackage")) {
            Object obj = params.get("perPackage");
            int newPerPackage = obj instanceof Number ? ((Number) obj).intValue() : 30;
            medicine.setPackageQuantity(newPerPackage);
            
            int totalPackages = medicine.getTotalPackages() != null ? medicine.getTotalPackages() : 1;
            int newTotalStock = totalPackages * newPerPackage;
            int currentStock = medicine.getCurrentStock() != null ? medicine.getCurrentStock() : 0;
            int oldTotalStock = medicine.getTotalStock() != null ? medicine.getTotalStock() : 0;
            
            medicine.setTotalStock(newTotalStock);
            if (oldTotalStock > 0) {
                int newCurrentStock = (int) ((double) currentStock / oldTotalStock * newTotalStock);
                medicine.setCurrentStock(Math.max(0, Math.min(newCurrentStock, newTotalStock)));
            } else {
                medicine.setCurrentStock(newTotalStock);
            }
        }
        if (params.containsKey("reminderTimes")) {
            List<String> times = (List<String>) params.get("reminderTimes");
            medicine.setReminderTimes(JSON.toJSONString(times));
        }
        if (params.containsKey("precautions")) {
            medicine.setPrecautions((String) params.get("precautions"));
        }
        
        boolean frequencyChanged = params.containsKey("frequency");
        boolean reminderTimesChanged = params.containsKey("reminderTimes");
        
        userMedicineMapper.update(medicine);
        
        if (frequencyChanged || reminderTimesChanged) {
            regenerateMedicationLogs(medicine.getId(), parseReminderTimes(medicine.getReminderTimes()));
        }
        
        syncMedicineBoxSlot(medicine.getId(), medicine.getCurrentStock(), medicine.getDosagePerTime());
        
        checkAndCreateStockAlert(medicine.getId());
        
        String afterData = JSON.toJSONString(medicine);
        User operator = userMapper.getById(userId.longValue());
        String operatorName = operator != null ? (operator.getNickname() != null ? operator.getNickname() : operator.getUsername()) : "未知";
        String operatorRole = getUserRole(userId.longValue(), medicine.getUserId().longValue());
        logOperation(medicineId, medicine.getUserId().longValue(), userId.longValue(), 
                    operatorName, operatorRole, MedicineOperationLog.TYPE_UPDATE, beforeData, afterData, null);
        
        return Result.success(medicine);
    }

    @Transactional
    public Result deleteMedicine(Integer userId, Integer medicineId, String deleteReason) {
        UserMedicine medicine = userMedicineMapper.selectByIdIncludeDeleted(medicineId);
        if (medicine == null) {
            return Result.error("药品不存在");
        }
        
        String operatorRole = getUserRole(userId.longValue(), medicine.getUserId().longValue());
        
        if (MedicineOperationLog.ROLE_ELDER.equals(operatorRole) || 
            MedicineOperationLog.ROLE_COMMUNITY.equals(operatorRole)) {
            return Result.error(403, "权限不足：仅子女角色可执行删除操作");
        }
        
        if (!canOperate(userId.longValue(), medicine.getUserId().longValue())) {
            return Result.error("无权操作该药品");
        }
        
        if (medicine.getIsDeleted() != null && medicine.getIsDeleted() == 1) {
            return Result.error("该药品已被删除");
        }
        
        User operator = userMapper.getById(userId.longValue());
        String operatorName = operator != null ? (operator.getNickname() != null ? operator.getNickname() : operator.getUsername()) : "未知";
        
        String beforeData = JSON.toJSONString(medicine);
        
        int totalStock = medicine.getTotalStock() != null ? medicine.getTotalStock() : 0;
        int packageQuantity = medicine.getPackageQuantity() != null ? medicine.getPackageQuantity() : 0;
        int totalPills = totalStock > 0 ? totalStock : (medicine.getTotalPackages() != null ? medicine.getTotalPackages() * packageQuantity : 0);
        BigDecimal initialWeight = new BigDecimal(totalPills).multiply(new BigDecimal("1.0"));
        
        userMedicineMapper.softDelete(medicineId, userId.longValue(), operatorName, operatorRole, deleteReason);
        
        String unbindResult = unbindMedicineFromSlot(medicineId);
        
        clearStockAlerts(medicineId);
        
        clearMedicationLogs(medicineId);
        
        logOperation(medicineId, medicine.getUserId().longValue(), userId.longValue(), 
                    operatorName, operatorRole, MedicineOperationLog.TYPE_DELETE, beforeData, null, 
                    "删除原因: " + (deleteReason != null ? deleteReason : "未填写"));
        
        Map<String, Object> result = new HashMap<>();
        result.put("message", "删除成功");
        result.put("medicineName", medicine.getCustomName());
        result.put("totalPills", totalPills);
        result.put("initialWeight", initialWeight);
        result.put("unbindResult", unbindResult);
        
        return Result.success(result);
    }
    
    private void clearStockAlerts(Integer medicineId) {
        try {
            int cleared = jdbcTemplate.update(
                "DELETE FROM medicine_stock_alert WHERE user_medicine_id = ?",
                medicineId
            );
            log.info("已清除药品 {} 的 {} 条库存预警", medicineId, cleared);
        } catch (Exception e) {
            log.error("清除库存预警失败", e);
        }
    }
    
    private void clearMedicationLogs(Integer medicineId) {
        try {
            String today = java.time.LocalDate.now().toString();
            int cleared = jdbcTemplate.update(
                "DELETE FROM medication_logs WHERE user_medicine_id = ? AND DATE(scheduled_time) >= ?",
                medicineId, today
            );
            log.info("已清除药品 {} 的 {} 条未来服药记录（保留历史记录）", medicineId, cleared);
        } catch (Exception e) {
            log.error("清除服药记录失败", e);
        }
    }
    
    private void syncMedicineBoxSlot(Integer medicineId, int currentStock, BigDecimal dosagePerTime) {
        try {
            BigDecimal currentWeight = new BigDecimal(currentStock);
            BigDecimal dosageWeight = dosagePerTime != null ? dosagePerTime : new BigDecimal("1");
            
            String takeStatus = currentStock > 0 ? "pending" : "empty";
            
            Integer pillsPerDose = dosagePerTime != null ? dosagePerTime.intValue() : 1;
            
            Map<String, Object> medicine = jdbcTemplate.queryForMap(
                "SELECT frequency_per_day FROM user_medicines WHERE id = ?",
                medicineId
            );
            
            Integer frequencyPerDay = medicine.get("frequency_per_day") != null ?
                ((Number) medicine.get("frequency_per_day")).intValue() : 1;
            
            int availableDoses = currentStock / pillsPerDose;
            int remainingPills = availableDoses * pillsPerDose;
            int availableDays = frequencyPerDay > 0 ? availableDoses / frequencyPerDay : 0;
            
            int updated = jdbcTemplate.update(
                "UPDATE medicine_box_slot SET current_weight = ?, dosage_weight = ?, take_status = ?, " +
                "remaining_pills = ?, available_doses = ?, available_days = ?, update_time = NOW() " +
                "WHERE user_medicine_id = ?",
                currentWeight, dosageWeight, takeStatus, remainingPills, availableDoses, availableDays, medicineId
            );
            
            if (updated > 0) {
                log.info("[药仓同步] 药品 {} 已同步到药仓，库存: {}，剂量: {}，状态: {}，剩余粒数: {}，可服: {}次/{}天", 
                    medicineId, currentStock, dosageWeight, takeStatus, remainingPills, availableDoses, availableDays);
            }
        } catch (Exception e) {
            log.error("[药仓同步] 同步失败", e);
        }
    }
    
    private String unbindMedicineFromSlot(Integer medicineId) {
        try {
            int updated = jdbcTemplate.update(
                "UPDATE medicine_box_slot SET " +
                "user_medicine_id = NULL, " +
                "bind_time_slot = NULL, " +
                "slot_name = NULL, " +
                "current_count = 0, " +
                "max_count = 30, " +
                "dosage_weight = NULL, " +
                "current_weight = 0.00, " +
                "take_status = 'pending', " +
                "last_take_time = NULL, " +
                "weight_before_take = NULL, " +
                "weight_after_take = NULL, " +
                "status = 1, " +
                "update_time = NOW() " +
                "WHERE user_medicine_id = ?",
                medicineId
            );
            if (updated > 0) {
                return "已清除 " + updated + " 个药仓数据";
            }
            return "该药品未绑定药仓";
        } catch (Exception e) {
            return "清除药仓数据失败: " + e.getMessage();
        }
    }
    
    private String getUserRole(Long operatorId, Long medicineOwnerId) {
        if (operatorId.equals(medicineOwnerId)) {
            return MedicineOperationLog.ROLE_ELDER;
        }
        
        User operator = userMapper.getById(operatorId);
        if (operator != null && "admin".equals(operator.getRole())) {
            return MedicineOperationLog.ROLE_COMMUNITY;
        }
        
        Friendship friendship = friendMapper.getAcceptedFriendship(operatorId, medicineOwnerId);
        if (friendship != null) {
            return MedicineOperationLog.ROLE_CHILD;
        }
        
        Friendship reverseFriendship = friendMapper.getAcceptedFriendship(medicineOwnerId, operatorId);
        if (reverseFriendship != null) {
            return MedicineOperationLog.ROLE_CHILD;
        }
        
        return MedicineOperationLog.ROLE_COMMUNITY;
    }
    
    private boolean canOperate(Long operatorId, Long medicineOwnerId) {
        if (operatorId.equals(medicineOwnerId)) {
            return true;
        }
        
        Friendship friendship = friendMapper.getAcceptedFriendship(operatorId, medicineOwnerId);
        if (friendship != null) {
            return true;
        }
        
        Friendship reverseFriendship = friendMapper.getAcceptedFriendship(medicineOwnerId, operatorId);
        return reverseFriendship != null;
    }
    
    private void logOperation(Integer medicineId, Long userId, Long operatorId, 
                              String operatorName, String operatorRole,
                              String operationType, String beforeData, String afterData, String remark) {
        MedicineOperationLog log = new MedicineOperationLog();
        log.setUserMedicineId(medicineId);
        log.setUserId(userId);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setOperatorRole(operatorRole);
        log.setOperationType(operationType);
        log.setBeforeData(beforeData);
        log.setAfterData(afterData);
        log.setRemark(remark);
        operationLogMapper.insert(log);
    }

    @Transactional
    public Result markAsTaken(Integer userId, Integer medicineId) {
        UserMedicine medicine = userMedicineMapper.selectByIdForUpdate(medicineId);
        if (medicine == null) {
            log.warn("[服药记录] 药品不存在, medicineId={}", medicineId);
            return Result.error("药品不存在");
        }
        
        if (!canOperate(userId.longValue(), medicine.getUserId().longValue())) {
            log.warn("[服药记录] 无权操作, userId={}, medicineUserId={}", userId, medicine.getUserId());
            return Result.error("无权操作该药品");
        }
        
        if (medicine.getIsDeleted() != null && medicine.getIsDeleted() == 1) {
            log.warn("[服药记录] 药品已被删除, medicineId={}", medicineId);
            return Result.error("该药品已被删除");
        }
        
        LocalDateTime now = LocalDateTime.now();
        LocalTime currentTime = now.toLocalTime();
        List<String> reminderTimes = parseReminderTimes(medicine.getReminderTimes());
        
        // 获取当前时间对应的计划服药信息
        PlannedDoseInfo doseInfo = getPlannedDoseInfo(reminderTimes, currentTime);
        
        // 判断当前服药行为类型
        MedicationAction action = judgeMedicationAction(currentTime, doseInfo);
        String medicineName = medicine.getCustomName() != null ? medicine.getCustomName() : medicine.getMedicineName();
        
        // 如果是过早服药，拦截并提示
        if ("too_early".equals(action.type)) {
            log.warn("[过早服药拦截] userId={}, medicineId={}, plannedTime={}, currentTime={}", 
                userId, medicineId, doseInfo.plannedTime, currentTime);
            
            Map<String, Object> warningData = new HashMap<>();
            warningData.put("tooEarly", true);
            warningData.put("message", action.message);
            warningData.put("detailMessage", action.detailMessage);
            warningData.put("medicineName", medicineName);
            warningData.put("plannedTime", doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            warningData.put("earlyAllowTime", doseInfo.earlyAllowTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            warningData.put("currentTime", currentTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            
            return Result.error(400, action.message, warningData);
        }

        if ("missed".equals(action.type)) {
            log.warn("[漏服拦截] userId={}, medicineId={}, plannedTime={}, makeupDeadTime={}, currentTime={}", 
                userId, medicineId, doseInfo.plannedTime, doseInfo.makeupDeadTime, currentTime);
            
            Map<String, Object> warningData = new HashMap<>();
            warningData.put("missed", true);
            warningData.put("message", action.message);
            warningData.put("detailMessage", action.detailMessage);
            warningData.put("medicineName", medicineName);
            warningData.put("plannedTime", doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            warningData.put("makeupDeadTime", doseInfo.makeupDeadTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            warningData.put("currentTime", currentTime.format(DateTimeFormatter.ofPattern("HH:mm")));
            
            return Result.error(400, action.message, warningData);
        }

        LocalDateTime scheduledTime = LocalDateTime.of(now.toLocalDate(), doseInfo.plannedTime);
        MedicationLog takenLog = medicationLogMapper.selectTakenTodayByUserMedicineAndTime(medicineId, scheduledTime);
        
        if (takenLog != null) {
            log.warn("[重复服药警告] 用户已服用过该药品, userId={}, medicineId={}, medicineName={}, takenTime={}", 
                userId, medicineId, medicineName, takenLog.getTakenTime());
            
            Map<String, Object> warningData = new HashMap<>();
            warningData.put("duplicate", true);
            warningData.put("message", "您已服用过本次药品，请勿重复服用！");
            warningData.put("medicineName", medicineName);
            warningData.put("takenTime", takenLog.getTakenTime() != null ? takenLog.getTakenTime().toString() : "");
            
            return Result.error(400, "您已服用过本次药品，请勿重复服用！", warningData);
        }
        
        int dosage = medicine.getDosagePerTime().intValue();
        int stockBefore = medicine.getCurrentStock();
        
        if (stockBefore < dosage) {
            log.warn("[服药记录] 库存不足, medicineId={}, stock={}, dosage={}", medicineId, stockBefore, dosage);
            return Result.error("库存不足，当前库存：" + stockBefore + "，需要：" + dosage);
        }

        MedicationLog pendingLog = findOrCreatePendingLog(medicineId, now, doseInfo);
        
        if (pendingLog != null) {
            pendingLog.setTakenTime(now);
            pendingLog.setDosageTaken(new BigDecimal(dosage));
            if ("early".equals(action.type)) {
                pendingLog.setStatus("taken_early");
            } else if ("makeup".equals(action.type)) {
                pendingLog.setStatus("taken_makeup");
            } else {
                pendingLog.setStatus("taken");
            }
            medicationLogMapper.updateTaken(pendingLog.getId(), now, new BigDecimal(dosage), pendingLog.getStatus());
        }
        
        int rowsAffected = userMedicineMapper.decrementStock(medicineId, dosage);
        if (rowsAffected == 0) {
            log.error("[服药记录] 库存更新失败, medicineId={}, dosage={}", medicineId, dosage);
            throw new RuntimeException("库存更新失败，请重试");
        }
        
        UserMedicine updatedMedicine = userMedicineMapper.selectById(medicineId);
        int stockAfter = updatedMedicine != null ? updatedMedicine.getCurrentStock() : 0;
        
        log.info("[服药记录] 服药成功, userId={}, medicineId={}, medicineName={}, dosage={}, stockBefore={}, stockAfter={}, actionType={}", 
            userId, medicineId, medicineName, dosage, stockBefore, stockAfter, action.type);
        
        if (stockAfter == 0) {
            resetSlotWeightWhenStockEmpty(medicineId);
        }
        
        checkAndCreateStockAlert(medicineId);
        
        lockMedicineBoxUntilNextDose(medicine.getUserId().longValue(), reminderTimes, doseInfo);
        
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("message", action.message);
        resultData.put("detailMessage", action.detailMessage);
        resultData.put("dosage", dosage);
        resultData.put("stockBefore", stockBefore);
        resultData.put("stockAfter", stockAfter);
        resultData.put("actionType", action.type);
        resultData.put("plannedTime", doseInfo.plannedTime.format(DateTimeFormatter.ofPattern("HH:mm")));
        resultData.put("isMakeup", "makeup".equals(action.type));
        resultData.put("isEarly", "early".equals(action.type));
        
        return Result.success(resultData);
    }

    private void lockMedicineBoxUntilNextDose(Long userId, List<String> reminderTimes, PlannedDoseInfo currentDoseInfo) {
        try {
            MedicineBox box = medicineBoxMapper.selectByUserId(userId);
            if (box == null) {
                return;
            }
            
            LocalDateTime now = LocalDateTime.now();
            List<LocalTime> sortedTimes = new ArrayList<>();
            for (String timeStr : reminderTimes) {
                try {
                    sortedTimes.add(LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")));
                } catch (Exception e) {
                }
            }
            Collections.sort(sortedTimes);
            
            if (sortedTimes.isEmpty()) {
                return;
            }
            
            LocalTime nextDoseTime = null;
            for (LocalTime time : sortedTimes) {
                if (time.isAfter(currentDoseInfo.plannedTime)) {
                    nextDoseTime = time;
                    break;
                }
            }
            
            LocalDateTime lockedUntil;
            if (nextDoseTime != null) {
                lockedUntil = LocalDateTime.of(now.toLocalDate(), nextDoseTime);
            } else {
                lockedUntil = LocalDateTime.of(now.toLocalDate().plusDays(1), sortedTimes.get(0));
            }
            
            box.setIsLocked(true);
            box.setLockedUntil(lockedUntil);
            box.setUpdateTime(now);
            medicineBoxMapper.update(box);
            
            log.info("[智能药盒锁定] 用户 {} 的智能药盒已锁定至 {}", userId, lockedUntil);
        } catch (Exception e) {
            log.error("[智能药盒锁定] 锁定失败", e);
        }
    }

    @Transactional
    public Result markAllAsTaken(Integer userId) {
        List<UserMedicine> medicines = userMedicineMapper.selectByUserId(userId);
        int count = 0;
        int totalDosage = 0;
        List<String> skippedMessages = new ArrayList<>();
        
        for (UserMedicine med : medicines) {
            med = userMedicineMapper.selectByIdForUpdate(med.getId());
            if (med == null || (med.getIsDeleted() != null && med.getIsDeleted() == 1)) {
                continue;
            }
            
            Result result = markAsTaken(userId, med.getId());
            if (result.getCode() == 200) {
                Map<String, Object> data = (Map<String, Object>) result.getData();
                count++;
                totalDosage += data != null && data.get("dosage") != null ? (Integer) data.get("dosage") : 0;
            } else {
                String medicineName = med.getCustomName() != null ? med.getCustomName() : med.getMedicineName();
                skippedMessages.add(medicineName + ": " + result.getMsg());
                log.info("[批量服药] 跳过药品 {}, 原因: {}", medicineName, result.getMsg());
            }
        }
        
        log.info("[批量服药] 完成, userId={}, count={}, totalDosage={}", userId, count, totalDosage);
        
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("count", count);
        resultData.put("totalDosage", totalDosage);
        resultData.put("skippedMessages", skippedMessages);
        
        return Result.success(resultData);
    }

    public List<Map<String, Object>> getTodayLogs(Integer userId) {
        List<MedicationLog> logs = medicationLogMapper.selectTodayByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (MedicationLog log : logs) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", log.getId());
            item.put("medicineName", log.getMedicineName());
            item.put("scheduledTime", log.getScheduledTime());
            item.put("takenTime", log.getTakenTime());
            item.put("status", log.getStatus());
            item.put("dosageTaken", log.getDosageTaken());
            item.put("unit", log.getDoseUnit());
            item.put("reminderTimes", log.getReminderTimes());
            result.add(item);
        }
        
        return result;
    }

    public List<Map<String, Object>> getStockAlerts(Integer userId) {
        List<MedicineStockAlert> alerts = stockAlertMapper.selectUnreadByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (MedicineStockAlert alert : alerts) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", alert.getId());
            item.put("medicineName", alert.getMedicineName());
            item.put("alertType", alert.getAlertType());
            item.put("alertLevel", alert.getAlertLevel());
            item.put("message", alert.getMessage());
            item.put("daysRemaining", alert.getDaysRemaining());
            item.put("createTime", alert.getCreateTime());
            result.add(item);
        }
        
        return result;
    }

    public int getLowStockCount(Integer userId) {
        List<UserMedicine> lowStock = userMedicineMapper.selectLowStockByUserId(userId, 2);
        return lowStock.size();
    }

    public Map<String, Object> getBoxStatus(Integer userId) {
        MedicineBox box = medicineBoxMapper.selectByUserId(Long.valueOf(userId));
        Map<String, Object> result = new HashMap<>();
        
        if (box != null) {
            result.put("deviceId", box.getDeviceId());
            result.put("boxName", box.getBoxName());
            result.put("status", box.getStatus());
            result.put("batteryLevel", box.getBatteryLevel());
            result.put("lastSyncTime", box.getLastSyncTime());
            result.put("isOnline", box.getStatus() == 1);
        } else {
            result.put("isOnline", false);
            result.put("boxName", "未绑定药盒");
        }
        
        return result;
    }

    public MedicineInfo getMedicineByBarcode(String barcode) {
        return medicineInfoMapper.selectByBarcode(barcode);
    }
    
    public String extractBarcodeFromImage(java.io.InputStream imageStream) {
        try {
            javax.imageio.ImageIO.scanForPlugins();
            java.awt.image.BufferedImage image = javax.imageio.ImageIO.read(imageStream);
            if (image == null) {
                return null;
            }
            
            com.google.zxing.BinaryBitmap binaryBitmap = new com.google.zxing.BinaryBitmap(
                new com.google.zxing.common.HybridBinarizer(
                    new com.google.zxing.client.j2se.BufferedImageLuminanceSource(image)
                )
            );
            
            com.google.zxing.MultiFormatReader reader = new com.google.zxing.MultiFormatReader();
            com.google.zxing.Result result = reader.decode(binaryBitmap);
            return result.getText();
        } catch (Exception e) {
            System.err.println("条形码识别失败: " + e.getMessage());
            return null;
        }
    }
    
    public Map<String, Object> identifyMedicineName(String imageUrl) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String name = medicineService.extractMedicineName(imageUrl);
            String ocrText = medicineService.extractTextFromImage(imageUrl);
            
            result.put("name", name);
            result.put("ocrText", ocrText);
        } catch (Exception e) {
            result.put("name", "");
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    public Map<String, Object> identifyMedicineInstruction(String imageUrl) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            MedicineOcrService.MedicineOcrResult ocrResult = medicineService.extractMedicineInfo(imageUrl);
            String ocrText = medicineService.extractTextFromImage(imageUrl);
            
            result.put("spec", ocrResult.getSpec());
            result.put("dosagePerTime", ocrResult.getDosagePerTime());
            result.put("unit", ocrResult.getUnit() != null ? ocrResult.getUnit() : "粒");
            result.put("dosageMg", ocrResult.getDosageMg());
            result.put("frequency", ocrResult.getFrequency());
            result.put("precautions", ocrResult.getPrecautions());
            result.put("type", guessMedicineType(ocrResult.getName()));
            result.put("ocrText", ocrText);
            result.put("rawText", ocrResult.getRawText());
            result.put("name", ocrResult.getName());
        } catch (Exception e) {
            result.put("error", e.getMessage());
        }
        
        return result;
    }
    
    private Integer parseDosageNumber(String dosage) {
        if (dosage == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(dosage);
        if (m.find()) return Integer.parseInt(m.group(1));
        return null;
    }
    
    private String parseUnit(String dosage) {
        if (dosage == null) return "粒";
        if (dosage.contains("片")) return "片";
        if (dosage.contains("袋")) return "袋";
        if (dosage.contains("毫升")) return "毫升";
        return "粒";
    }
    
    private Integer parseFrequencyNumber(String frequency) {
        if (frequency == null) return null;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d+)").matcher(frequency);
        if (m.find()) return Integer.parseInt(m.group(1));
        return null;
    }
    
    private String guessMedicineType(String name) {
        if (name == null) return "其他";
        String lowerName = name.toLowerCase();
        
        if (lowerName.contains("降脂") || lowerName.contains("他汀")) return "降脂药";
        if (lowerName.contains("降压") || lowerName.contains("氨氯地平") || lowerName.contains("硝苯地平")) return "降压药";
        if (lowerName.contains("降糖") || lowerName.contains("二甲双胍")) return "降糖药";
        if (lowerName.contains("感冒") || lowerName.contains("布洛芬")) return "感冒药";
        if (lowerName.contains("抗生素") || lowerName.contains("阿莫西林") || lowerName.contains("头孢") || lowerName.contains("红霉素")) return "抗生素";
        if (lowerName.contains("胃") || lowerName.contains("奥美拉唑")) return "胃药";
        if (lowerName.contains("钙")) return "钙片";
        return "其他";
    }

    public Result processVoiceInput(Integer userId, String voiceText) {
        Map<String, Object> extractedInfo = medicineVoiceExtractor.extractMedicineInfo(voiceText);
        
        if (extractedInfo == null || extractedInfo.isEmpty()) {
            return Result.error("未能识别药品信息，请按格式说明：药物名称：XX，剂量：XX粒，频次：一天X次，一次X粒");
        }
        
        return Result.success(extractedInfo);
    }

    public String getNextGlobalReminder(Integer userId) {
        List<UserMedicine> medicines = userMedicineMapper.selectByUserId(userId);
        LocalTime now = LocalTime.now();
        LocalTime nextTime = null;
        
        for (UserMedicine med : medicines) {
            List<String> times = parseReminderTimes(med.getReminderTimes());
            for (String timeStr : times) {
                try {
                    LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                    if (time.isAfter(now)) {
                        if (nextTime == null || time.isBefore(nextTime)) {
                            nextTime = time;
                        }
                    }
                } catch (Exception e) {
                }
            }
        }
        
        if (nextTime != null) {
            return nextTime.format(DateTimeFormatter.ofPattern("HH:mm"));
        }
        return "明天 08:00";
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

    private List<String> parseReminderTimes(String reminderTimes) {
        if (reminderTimes == null || reminderTimes.isEmpty()) {
            return Arrays.asList("08:00", "18:00");
        }
        
        try {
            return JSON.parseArray(reminderTimes, String.class);
        } catch (Exception e) {
            return Arrays.asList("08:00", "18:00");
        }
    }

    private void generateMedicationLogs(Integer userMedicineId, List<String> reminderTimes) {
        LocalDate today = LocalDate.now();
        LocalDate endDate = today.plusDays(7);
        
        for (LocalDate date = today; date.isBefore(endDate); date = date.plusDays(1)) {
            for (String timeStr : reminderTimes) {
                try {
                    LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                    LocalDateTime scheduledTime = LocalDateTime.of(date, time);
                    
                    MedicationLog log = new MedicationLog();
                    log.setUserMedicineId(userMedicineId);
                    log.setScheduledTime(scheduledTime);
                    log.setStatus("pending");
                    log.setSource("system");
                    
                    medicationLogMapper.insert(log);
                } catch (Exception e) {
                }
            }
        }
    }

    private void regenerateMedicationLogs(Integer userMedicineId, List<String> reminderTimes) {
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        
        medicationLogMapper.deleteFuturePendingLogs(userMedicineId, now);
        
        LocalDate endDate = today.plusDays(7);
        for (LocalDate date = today; date.isBefore(endDate); date = date.plusDays(1)) {
            for (String timeStr : reminderTimes) {
                try {
                    LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                    LocalDateTime scheduledTime = LocalDateTime.of(date, time);
                    
                    if (scheduledTime.isAfter(now)) {
                        MedicationLog existingLog = medicationLogMapper.selectPendingByUserMedicineAndTime(userMedicineId, scheduledTime);
                        if (existingLog != null) {
                            continue;
                        }
                        
                        MedicationLog log = new MedicationLog();
                        log.setUserMedicineId(userMedicineId);
                        log.setScheduledTime(scheduledTime);
                        log.setStatus("pending");
                        log.setSource("system");
                        
                        medicationLogMapper.insert(log);
                    }
                } catch (Exception e) {
                }
            }
        }
    }

    private MedicationLog findOrCreatePendingLog(Integer userMedicineId, LocalDateTime time, PlannedDoseInfo doseInfo) {
        LocalDate today = time.toLocalDate();
        LocalDateTime scheduledTime = LocalDateTime.of(today, doseInfo.plannedTime);
        MedicationLog log = medicationLogMapper.selectPendingByUserMedicineAndTime(userMedicineId, scheduledTime);
        
        if (log != null) {
            return log;
        }
        
        MedicationLog newLog = new MedicationLog();
        newLog.setUserMedicineId(userMedicineId);
        newLog.setScheduledTime(scheduledTime);
        newLog.setStatus("pending");
        newLog.setSource("manual");
        medicationLogMapper.insert(newLog);
        
        return newLog;
    }

    public Map<String, Object> checkMakeupDoseEligibility(Integer userMedicineId) {
        Map<String, Object> result = new HashMap<>();
        result.put("canMakeup", false);
        result.put("reason", "");
        result.put("minutesToNextDose", -1);
        
        UserMedicine medicine = userMedicineMapper.selectById(userMedicineId);
        if (medicine == null) {
            result.put("reason", "药品不存在");
            return result;
        }
        
        List<String> reminderTimes = parseReminderTimes(medicine.getReminderTimes());
        if (reminderTimes.isEmpty()) {
            result.put("reason", "未设置提醒时间");
            return result;
        }
        
        LocalTime now = LocalTime.now();
        LocalDate today = LocalDate.now();
        
        LocalTime nextReminderTime = null;
        LocalTime missedReminderTime = null;
        
        List<LocalTime> sortedTimes = new ArrayList<>();
        for (String timeStr : reminderTimes) {
            try {
                sortedTimes.add(LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")));
            } catch (Exception e) {
            }
        }
        Collections.sort(sortedTimes);
        
        for (LocalTime time : sortedTimes) {
            if (time.isAfter(now)) {
                nextReminderTime = time;
                break;
            }
        }
        
        MedicationLog medLog = null;
        LocalDateTime scheduledTime = null;

        for (int i = sortedTimes.size() - 1; i >= 0; i--) {
            LocalTime time = sortedTimes.get(i);
            log.info("[补服检查] 检查时间: {}, 当前时间: {}", time, now);
            if (time.isBefore(now) || time.equals(now)) {
                missedReminderTime = time;
                scheduledTime = LocalDateTime.of(today, missedReminderTime);
                medLog = medicationLogMapper.selectPendingByUserMedicineAndTime(userMedicineId, scheduledTime);
                
                boolean isTaken = false;
                if (medLog != null && "taken".equals(medLog.getStatus())) {
                    isTaken = true;
                    log.info("[补服检查] 时间 {}: 已找到pending记录且状态为taken", time);
                } else if (medLog == null) {
                        MedicationLog takenLog = medicationLogMapper.selectTakenTodayByUserMedicineAndTime(userMedicineId, scheduledTime);
                        if (takenLog != null) {
                            isTaken = true;
                            log.info("[补服检查] 时间 {}: 已找到taken记录", time);
                        } else {
                            log.info("[补服检查] 时间 {}: 未找到任何记录", time);
                        }
                    } else {
                        log.info("[补服检查] 时间 {}: pending记录状态为 {}", time, medLog.getStatus());
                    }
                    
                    if (!isTaken) {
                        log.info("[补服检查] 时间 {}: 未服用，使用此记录", time);
                        break;
                    } else {
                        log.info("[补服检查] 时间 {}: 已服用，继续往前找", time);
                        medLog = null;
                        missedReminderTime = null;
                        scheduledTime = null;
                    }
                }
            }
        
        if (missedReminderTime == null) {
            result.put("reason", "当前没有漏服的药品");
            return result;
        }
        if (medLog == null) {
            medLog = new MedicationLog();
            medLog.setUserMedicineId(userMedicineId);
            medLog.setScheduledTime(scheduledTime);
            medLog.setStatus("pending");
            medLog.setSource("manual");
            medicationLogMapper.insert(medLog);
            log.info("[补服检查] 为药品 {} 创建新的服药记录, scheduledTime={}", userMedicineId, scheduledTime);
        }
        
        if (nextReminderTime == null) {
            nextReminderTime = sortedTimes.get(0);
        }
        
        long minutesToNext = java.time.Duration.between(now, nextReminderTime).toMinutes();
        if (nextReminderTime.isBefore(now)) {
            minutesToNext = java.time.Duration.between(now, nextReminderTime.plusHours(24)).toMinutes();
        }
        
        result.put("minutesToNextDose", minutesToNext);
        result.put("missedTime", missedReminderTime.toString());
        result.put("nextTime", nextReminderTime.toString());
        
        long intervalMinutes;
        LocalTime nextDoseTime;
        if (sortedTimes.size() == 1) {
            intervalMinutes = 24 * 60;
            nextDoseTime = missedReminderTime.plusHours(24);
        } else {
            int missedIndex = sortedTimes.indexOf(missedReminderTime);
            if (missedIndex < sortedTimes.size() - 1) {
                nextDoseTime = sortedTimes.get(missedIndex + 1);
            } else {
                nextDoseTime = sortedTimes.get(0).plusHours(24);
            }
            intervalMinutes = java.time.Duration.between(missedReminderTime, nextDoseTime).toMinutes();
        }
        
        long makeupWindowMinutes = intervalMinutes / 2;
        long minutesSinceMissed = java.time.Duration.between(missedReminderTime, now).toMinutes();
        if (minutesSinceMissed < 0) {
            minutesSinceMissed = minutesSinceMissed + 24 * 60;
        }
        
        log.info("[补服检查] 漏服时间={}, 下次服药时间={}, 间隔={}分钟, 补服窗口={}分钟, 已过去={}分钟", 
            missedReminderTime, nextDoseTime, intervalMinutes, makeupWindowMinutes, minutesSinceMissed);
        
        if (minutesSinceMissed > makeupWindowMinutes) {
            result.put("canMakeup", false);
            result.put("reason", "已超过补服窗口期，建议等待下次服药");
            return result;
        }
        
        if ("missed".equals(medLog.getStatus())) {
            result.put("canMakeup", true);
            result.put("reason", "该药品曾被标记为漏服，但仍可补服");
            result.put("logId", medLog.getId());
            return result;
        }
        
        result.put("canMakeup", true);
        result.put("reason", "可以补服");
        result.put("logId", medLog.getId());
        
        return result;
    }

    @Transactional
    public Result makeupDose(Integer userId, Integer medicineId) {
        log.info("[补服请求] userId={}, medicineId={}", userId, medicineId);
        
        Map<String, Object> eligibility = checkMakeupDoseEligibility(medicineId);
        Boolean canMakeup = (Boolean) eligibility.get("canMakeup");
        
        if (canMakeup == null || !canMakeup) {
            String reason = (String) eligibility.get("reason");
            log.warn("[补服拒绝] userId={}, medicineId={}, reason={}", userId, medicineId, reason);
            return Result.error(reason);
        }
        
        UserMedicine medicine = userMedicineMapper.selectByIdForUpdate(medicineId);
        if (medicine == null) {
            return Result.error("药品不存在");
        }
        
        if (!canOperate(userId.longValue(), medicine.getUserId().longValue())) {
            return Result.error("无权操作该药品");
        }
        
        int dosage = medicine.getDosagePerTime().intValue();
        int stockBefore = medicine.getCurrentStock();
        
        if (stockBefore < dosage) {
            return Result.error("库存不足，当前库存：" + stockBefore + "，需要：" + dosage);
        }
        
        Long logId = (Long) eligibility.get("logId");
        LocalDateTime now = LocalDateTime.now();
        
        medicationLogMapper.updateTaken(logId, now, new BigDecimal(dosage), "taken");
        
        jdbcTemplate.update(
            "UPDATE medication_logs SET source = 'makeup' WHERE id = ?",
            logId
        );
        
        int rowsAffected = userMedicineMapper.decrementStock(medicineId, dosage);
        if (rowsAffected == 0) {
            throw new RuntimeException("库存更新失败，请重试");
        }
        
        UserMedicine updatedMedicine = userMedicineMapper.selectById(medicineId);
        int stockAfter = updatedMedicine != null ? updatedMedicine.getCurrentStock() : 0;
        
        log.info("[补服成功] userId={}, medicineId={}, medicineName={}, dosage={}, stockBefore={}, stockAfter={}", 
            userId, medicineId, medicine.getCustomName() != null ? medicine.getCustomName() : medicine.getMedicineName(), 
            dosage, stockBefore, stockAfter);
        
        checkAndCreateStockAlert(medicineId);
        
        String nextDoseTimeStr = (String) eligibility.get("nextTime");
        Long minutesToNextDose = (Long) eligibility.get("minutesToNextDose");
        
        Map<String, Object> resultData = new HashMap<>();
        resultData.put("message", "补服成功");
        resultData.put("dosage", dosage);
        resultData.put("stockBefore", stockBefore);
        resultData.put("stockAfter", stockAfter);
        resultData.put("isMakeup", true);
        resultData.put("nextDoseTime", nextDoseTimeStr);
        resultData.put("minutesToNextDose", minutesToNextDose);
        
        return Result.success(resultData);
    }

    private LocalTime findClosestReminderTime(Integer userMedicineId, LocalTime currentTime) {
        UserMedicine med = userMedicineMapper.selectById(userMedicineId);
        if (med == null) return null;
        
        List<String> times = parseReminderTimes(med.getReminderTimes());
        LocalTime closest = null;
        long minDiff = Long.MAX_VALUE;
        
        for (String timeStr : times) {
            try {
                LocalTime time = LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm"));
                long diff = Math.abs(time.toSecondOfDay() - currentTime.toSecondOfDay());
                if (diff < minDiff && diff < 3600) {
                    minDiff = diff;
                    closest = time;
                }
            } catch (Exception e) {
            }
        }
        
        return closest;
    }

    private int calculateDosingIntervalMinutes(List<String> reminderTimes) {
        if (reminderTimes == null || reminderTimes.size() < 2) {
            return 12 * 60;
        }
        
        List<LocalTime> times = new ArrayList<>();
        for (String timeStr : reminderTimes) {
            try {
                times.add(LocalTime.parse(timeStr, DateTimeFormatter.ofPattern("HH:mm")));
            } catch (Exception e) {
            }
        }
        
        if (times.size() < 2) {
            return 12 * 60;
        }
        
        Collections.sort(times);

        int minInterval = 24 * 60;
        for (int i = 1; i < times.size(); i++) {
            int interval = times.get(i).toSecondOfDay() - times.get(i - 1).toSecondOfDay();
            interval = interval / 60;
            if (interval > 0 && interval < minInterval) {
                minInterval = interval;
            }
        }
        

        int crossDayInterval = (24 * 60 - times.get(times.size() - 1).toSecondOfDay() / 60) 
                             + times.get(0).toSecondOfDay() / 60;
        if (crossDayInterval > 0 && crossDayInterval < minInterval) {
            minInterval = crossDayInterval;
        }
        
        return minInterval;
    }


    private LocalTime calculateMakeupDeadTime(LocalTime plannedTime, int dosingIntervalMinutes) {
        int makeupMinutes = Math.max(dosingIntervalMinutes / 2, 2 * 60);
        return plannedTime.plusMinutes(makeupMinutes);
    }


    private LocalTime calculateEarlyAllowTime(LocalTime plannedTime, int dosingIntervalMinutes) {
        int earlyMinutes = 30;
        return plannedTime.minusMinutes(earlyMinutes);
    }


    private PlannedDoseInfo getPlannedDoseInfo(List<String> reminderTimes, LocalTime currentTime) {
        // 使用 MedicineBoxService 中已经修复好的统一逻辑
        MedicineBoxService.PlannedDoseInfo doseInfo = medicineBoxService.getPlannedDoseInfo(reminderTimes, currentTime);
        
        // 转换为 MedicineAssistantService 自己的 PlannedDoseInfo 类型
        PlannedDoseInfo info = new PlannedDoseInfo();
        info.plannedTime = doseInfo.plannedTime;
        info.earlyAllowTime = doseInfo.earlyAllowTime;
        info.makeupDeadTime = doseInfo.makeupDeadTime;
        info.dosingIntervalMinutes = doseInfo.dosingIntervalMinutes;
        info.isFirstDose = doseInfo.isFirstDose;
        info.isLastDose = doseInfo.isLastDose;
        
        log.info("[手动服药-时间窗口选择] 当前时间={}, 选择计划时间={}, 提前允许={}, 补服截止={}", 
            currentTime, info.plannedTime, info.earlyAllowTime, info.makeupDeadTime);
        
        return info;
    }


    private static class PlannedDoseInfo {
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

    private void checkAndCreateStockAlert(Integer userMedicineId) {
        UserMedicine med = userMedicineMapper.selectById(userMedicineId);
        if (med == null) return;
        
        int dailyUsage = med.getDosagePerTime().intValue() * med.getFrequencyPerDay();
        int daysLeft = dailyUsage > 0 ? med.getCurrentStock() / dailyUsage : 0;
        
        List<MedicineStockAlert> existingAlerts = stockAlertMapper.selectByUserMedicineId(userMedicineId);
        List<MedicineStockAlert> lowStockAlerts = existingAlerts.stream()
            .filter(a -> "low_stock".equals(a.getAlertType()) && a.getIsResolved() == 0)
            .toList();
        
        if (daysLeft < 4) {
            if (lowStockAlerts.isEmpty()) {
                MedicineStockAlert alert = new MedicineStockAlert();
                alert.setUserMedicineId(userMedicineId);
                alert.setAlertType("low_stock");
                int alertLevel;
                if (daysLeft < 2) {
                    alertLevel = 3;
                } else if (daysLeft < 3) {
                    alertLevel = 2;
                } else {
                    alertLevel = 1;
                }
                alert.setAlertLevel(alertLevel);
                alert.setMessage(String.format("%s库存不足，仅剩%d%s，可服用%d天",
                    med.getCustomName() != null ? med.getCustomName() : med.getMedicineName(),
                    med.getCurrentStock(), med.getDoseUnit(), daysLeft));
                alert.setDaysRemaining(daysLeft);
                stockAlertMapper.insert(alert);
            } else {
                MedicineStockAlert existingAlert = lowStockAlerts.get(0);
                int alertLevel;
                if (daysLeft < 2) {
                    alertLevel = 3;
                } else if (daysLeft < 3) {
                    alertLevel = 2;
                } else {
                    alertLevel = 1;
                }
                existingAlert.setAlertLevel(alertLevel);
                existingAlert.setMessage(String.format("%s库存不足，仅剩%d%s，可服用%d天",
                    med.getCustomName() != null ? med.getCustomName() : med.getMedicineName(),
                    med.getCurrentStock(), med.getDoseUnit(), daysLeft));
                existingAlert.setDaysRemaining(daysLeft);
                existingAlert.setIsRead(0);
                stockAlertMapper.update(existingAlert);
            }
        } else {
            for (MedicineStockAlert alert : lowStockAlerts) {
                alert.setIsResolved(1);
                stockAlertMapper.update(alert);
            }
        }
    }

    @Transactional
    public Result replenishMedicine(Integer userId, Integer medicineId, Integer packages) {
        UserMedicine medicine = userMedicineMapper.selectByIdIncludeDeleted(medicineId);
        if (medicine == null) {
            return Result.error("药品不存在");
        }
        
        if (!canOperate(userId.longValue(), medicine.getUserId().longValue())) {
            return Result.error("无权操作该药品");
        }
        
        if (medicine.getIsDeleted() != null && medicine.getIsDeleted() == 1) {
            return Result.error("该药品已被删除");
        }
        
        if (packages == null || packages <= 0) {
            packages = 1;
        }
        
        int perPackage = medicine.getPackageQuantity() != null ? medicine.getPackageQuantity() : 30;
        int addStock = packages * perPackage;
        
        medicine.setTotalStock(medicine.getTotalStock() + addStock);
        medicine.setCurrentStock(medicine.getCurrentStock() + addStock);
        medicine.setTotalPackages(medicine.getTotalPackages() + packages);
        
        userMedicineMapper.update(medicine);
        
        syncMedicineBoxSlot(medicine.getId(), medicine.getCurrentStock(), medicine.getDosagePerTime());
        
        List<MedicineStockAlert> alerts = stockAlertMapper.selectByUserMedicineId(medicineId);
        for (MedicineStockAlert alert : alerts) {
            if (alert.getIsResolved() == 0) {
                stockAlertMapper.markAsResolved(Long.valueOf(alert.getId()));
            }
        }
        
        Map<String, Object> result = new HashMap<>();
        result.put("addedStock", addStock);
        result.put("currentStock", medicine.getCurrentStock());
        result.put("message", String.format("已补充%d%s，共%d%s", packages, medicine.getPackageUnit(), addStock, medicine.getDoseUnit()));
        
        return Result.success(result);
    }
    
    public List<Map<String, Object>> getDeletedMedicines(Integer userId) {
        List<UserMedicine> medicines = userMedicineMapper.selectDeletedByUserId(userId);
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (UserMedicine med : medicines) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", med.getId());
            String medicineName = med.getCustomName() != null ? med.getCustomName() : med.getMedicineName();
            item.put("name", medicineName);
            item.put("displayName", medicineName + "（ID：" + String.format("%03d", med.getId()) + "）");
            item.put("type", med.getType());
            item.put("spec", med.getSpec());
            item.put("startDate", med.getStartDate());
            item.put("endDate", med.getEndDate());
            item.put("deletedTime", med.getDeletedTime());
            item.put("deletedBy", med.getDeletedBy());
            item.put("deletedByName", med.getDeletedByName());
            item.put("deletedByRole", med.getDeletedByRole());
            item.put("deleteReason", med.getDeleteReason());
            result.add(item);
        }
        return result;
    }
    
    @Transactional
    public Result restoreMedicine(Integer userId, Integer medicineId) {
        UserMedicine medicine = userMedicineMapper.selectByIdIncludeDeleted(medicineId);
        if (medicine == null) {
            return Result.error("药品不存在");
        }
        
        if (!canOperate(userId.longValue(), medicine.getUserId().longValue())) {
            return Result.error("无权操作该药品");
        }
        
        if (medicine.getIsDeleted() == null || medicine.getIsDeleted() != 1) {
            return Result.error("该药品未被删除");
        }
        
        String beforeData = JSON.toJSONString(medicine);
        
        userMedicineMapper.restore(medicineId);
        
        User operator = userMapper.getById(userId.longValue());
        String operatorName = operator != null ? (operator.getNickname() != null ? operator.getNickname() : operator.getUsername()) : "未知";
        String operatorRole = getUserRole(userId.longValue(), medicine.getUserId().longValue());
        logOperation(medicineId, medicine.getUserId().longValue(), userId.longValue(), 
                    operatorName, operatorRole, MedicineOperationLog.TYPE_RESTORE, beforeData, null, "恢复已删除药品");
        
        return Result.success("恢复成功");
    }

    public Result getMedicationAnomalies(Integer familyId) {
        List<Map<String, Object>> anomalies = new ArrayList<>();
        
        try {
            String sql = "SELECT ml.id, ml.user_medicine_id, ml.scheduled_time, ml.taken_time, " +
                        "ml.dosage_taken, ml.status, ml.source, ml.create_time, " +
                        "COALESCE(um.custom_name, mi.name) as medicine_name, um.dose_unit, " +
                        "um.user_id, u.nickname as elder_name " +
                        "FROM medication_logs ml " +
                        "LEFT JOIN user_medicines um ON ml.user_medicine_id = um.id " +
                        "LEFT JOIN medicine_info mi ON um.medicine_id = mi.id " +
                        "LEFT JOIN users u ON um.user_id = u.id " +
                        "WHERE um.user_id = ? AND ml.status IN ('overdose', 'missed') " +
                        "AND DATE(ml.create_time) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY) " +
                        "ORDER BY ml.create_time DESC LIMIT 50";
            
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, familyId);
            
            for (Map<String, Object> row : rows) {
                Map<String, Object> anomaly = new HashMap<>();
                anomaly.put("id", row.get("id"));
                anomaly.put("medicineName", row.get("medicine_name"));
                anomaly.put("elderName", row.get("elder_name"));
                anomaly.put("scheduledTime", row.get("scheduled_time"));
                anomaly.put("takenTime", row.get("taken_time"));
                anomaly.put("dosageTaken", row.get("dosage_taken"));
                anomaly.put("doseUnit", row.get("dose_unit"));
                anomaly.put("status", row.get("status"));
                anomaly.put("source", row.get("source"));
                anomaly.put("createTime", row.get("create_time"));
                
                String status = (String) row.get("status");
                if ("overdose".equals(status)) {
                    anomaly.put("type", "overdose");
                    anomaly.put("typeText", "多服");
                    anomaly.put("typeIcon", "exclamation-circle");
                    anomaly.put("typeColor", "#F59E0B");
                } else if ("missed".equals(status)) {
                    anomaly.put("type", "missed");
                    anomaly.put("typeText", "漏服");
                    anomaly.put("typeIcon", "times-circle");
                    anomaly.put("typeColor", "#EF4444");
                }
                
                anomalies.add(anomaly);
            }
            
            return Result.success(anomalies);
        } catch (Exception e) {
            log.error("获取服药异常记录失败", e);
            return Result.error("获取服药异常记录失败: " + e.getMessage());
        }
    }

    public Result getUnreadAnomalyCount(Integer userId) {
        try {
            List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(userId.longValue());
            int totalUnread = 0;
            
            for (Map<String, Object> friend : friends) {
                Long friendId = ((Number) friend.get("id")).longValue();
                String sql = "SELECT COUNT(*) FROM medication_logs ml " +
                            "LEFT JOIN user_medicines um ON ml.user_medicine_id = um.id " +
                            "WHERE um.user_id = ? AND ml.status IN ('overdose', 'missed') " +
                            "AND (ml.is_read IS NULL OR ml.is_read = 0) " +
                            "AND DATE(ml.create_time) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)";
                
                Integer count = jdbcTemplate.queryForObject(sql, Integer.class, friendId);
                if (count != null) {
                    totalUnread += count;
                }
            }
            
            return Result.success(totalUnread);
        } catch (Exception e) {
            log.error("获取未读异常数量失败", e);
            return Result.success(0);
        }
    }

    @Transactional
    public Result markAnomalyAsRead(Long anomalyId) {
        try {
            String sql = "UPDATE medication_logs SET is_read = 1 WHERE id = ?";
            jdbcTemplate.update(sql, anomalyId);
            return Result.success("已标记为已读");
        } catch (Exception e) {
            log.error("标记已读失败", e);
            return Result.error("标记已读失败");
        }
    }

    @Transactional
    public Result markAllAnomaliesAsRead(Integer familyId) {
        try {
            String sql = "UPDATE medication_logs ml " +
                        "LEFT JOIN user_medicines um ON ml.user_medicine_id = um.id " +
                        "SET ml.is_read = 1 " +
                        "WHERE um.user_id = ? AND ml.status IN ('overdose', 'missed') " +
                        "AND (ml.is_read IS NULL OR ml.is_read = 0)";
            jdbcTemplate.update(sql, familyId);
            return Result.success("已全部标记为已读");
        } catch (Exception e) {
            log.error("批量标记已读失败", e);
            return Result.error("批量标记已读失败");
        }
    }
}

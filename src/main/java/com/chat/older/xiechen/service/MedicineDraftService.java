package com.chat.older.xiechen.service;

import com.alibaba.fastjson2.JSON;
import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.*;
import com.chat.older.xiechen.handler.ChatHandler;
import com.chat.older.xiechen.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class MedicineDraftService {

    @Autowired
    private MedicineDraftMapper draftMapper;
    
    @Autowired
    private DraftReviewLogMapper reviewLogMapper;
    
    @Autowired
    private UserMedicineMapper userMedicineMapper;
    
    @Autowired
    private MedicationLogMapper medicationLogMapper;
    
    @Autowired
    private JdbcTemplate jdbcTemplate;
    
    @Autowired
    private FriendMapper friendMapper;
    
    @Autowired
    private UserMapper userMapper;
    
    @Autowired
    private ChatHandler chatHandler;
    
    @Autowired
    private MessageMapper messageMapper;
    
    @Autowired
    private EmergencyContactMapper emergencyContactMapper;

    @Transactional
    public Result createDraft(Long elderUserId, Map<String, Object> params) {
        MedicineDraft draft = new MedicineDraft();
        draft.setElderUserId(elderUserId);
        draft.setStatus(MedicineDraft.STATUS_PENDING);
        draft.setOperationType(MedicineDraft.OP_CREATE);
        
        if (params.get("confidence") != null) {
            Object confObj = params.get("confidence");
            draft.setConfidence(confObj instanceof Number ? 
                new BigDecimal(confObj.toString()) : new BigDecimal("0"));
        }
        
        draft.setMedicineName((String) params.get("medicineName"));
        draft.setMedicineType((String) params.getOrDefault("medicineType", params.get("type")));
        draft.setSpec((String) params.get("spec"));
        
        if (params.get("dosagePerTime") != null) {
            Object obj = params.get("dosagePerTime");
            draft.setDosagePerTime(obj instanceof Number ? new BigDecimal(obj.toString()) : new BigDecimal("1"));
        }
        
        String doseUnit = (String) params.getOrDefault("doseUnit", params.get("unit"));
        draft.setDoseUnit(doseUnit != null ? doseUnit : "片");
        
        Object freqObj = params.getOrDefault("frequencyPerDay", params.get("frequency"));
        if (freqObj != null) {
            draft.setFrequencyPerDay(freqObj instanceof Number ? ((Number) freqObj).intValue() : 2);
        } else {
            draft.setFrequencyPerDay(2);
        }
        
        List<String> reminderTimes = (List<String>) params.get("reminderTimes");
        if (reminderTimes != null && !reminderTimes.isEmpty()) {
            draft.setReminderTimes(JSON.toJSONString(reminderTimes));
        } else {
            draft.setReminderTimes("[\"08:00\",\"18:00\"]");
        }
        
        if (params.get("totalPackages") != null) {
            Object obj = params.get("totalPackages");
            draft.setTotalPackages(obj instanceof Number ? ((Number) obj).intValue() : 1);
        } else {
            draft.setTotalPackages(1);
        }
        
        Object pkgQtyObj = params.getOrDefault("packageQuantity", params.get("pillsPerBox"));
        if (pkgQtyObj != null) {
            draft.setPackageQuantity(pkgQtyObj instanceof Number ? ((Number) pkgQtyObj).intValue() : 30);
        } else {
            draft.setPackageQuantity(30);
        }
        
        draft.setPrecautions((String) params.get("precautions"));
        draft.setManufacturer((String) params.get("manufacturer"));
        draft.setApprovalNumber((String) params.get("approvalNumber"));
        
        String coverUrl = (String) params.getOrDefault("coverImageUrl", params.get("nameImageUrl"));
        draft.setCoverImageUrl(coverUrl);
        draft.setInstructionImageUrl((String) params.get("instructionImageUrl"));
        draft.setVoiceUrl((String) params.get("voiceUrl"));
        draft.setVoiceText((String) params.get("voiceText"));
        draft.setRawOcrText((String) params.get("rawOcrText"));
        
        String inputMethod = (String) params.getOrDefault("inputMethod", MedicineDraft.INPUT_MANUAL);
        draft.setInputMethod(inputMethod);
        draft.setBarcode((String) params.get("barcode"));
        
        draftMapper.insert(draft);
        
        notifyFamilyMembers(elderUserId, draft);
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", draft.getId());
        result.put("message", "已帮您存好用药信息草稿啦，等您的家人确认后就会出现在用药页面～");
        
        return Result.success(result);
    }
    
    private void notifyFamilyMembers(Long elderUserId, MedicineDraft draft) {
        User elder = userMapper.getById(elderUserId);
        if (elder == null) return;
        
        String elderName = elder.getNickname() != null ? elder.getNickname() : elder.getUsername();
        
        List<EmergencyContact> emergencyContacts = emergencyContactMapper.selectByUserIdAndReviewType(elderUserId, EmergencyContact.REVIEW_TYPE_MEDICINE);
        if (emergencyContacts == null || emergencyContacts.isEmpty()) {
            emergencyContacts = emergencyContactMapper.selectByUserId(elderUserId);
        }
        if (emergencyContacts == null || emergencyContacts.isEmpty()) return;
        
        for (EmergencyContact contact : emergencyContacts) {
            Long contactUserId = contact.getContactUserId();
            if (contactUserId == null) continue;
            
            String confidenceStr = "";
            if (draft.getConfidence() != null) {
                confidenceStr = String.format("，AI识别置信度%.0f%%", draft.getConfidence());
            }
            
            String inputMethodText = getInputMethodText(draft.getInputMethod());
            
            StringBuilder content = new StringBuilder();
            content.append("【用药草稿待审核】\n");
            content.append(elderName).append("新增了药品信息").append(confidenceStr).append("，请核对是否正确！\n\n");
            content.append("药品名称：").append(draft.getMedicineName() != null ? draft.getMedicineName() : "待确认").append("\n");
            if (draft.getSpec() != null) {
                content.append("药品规格：").append(draft.getSpec()).append("\n");
            }
            if (draft.getDosagePerTime() != null) {
                content.append("每次用量：").append(draft.getDosagePerTime())
                       .append(draft.getDoseUnit() != null ? draft.getDoseUnit() : "片").append("\n");
            }
            if (draft.getFrequencyPerDay() != null) {
                content.append("每日次数：").append(draft.getFrequencyPerDay()).append("次\n");
            }
            content.append("\n录入方式：").append(inputMethodText).append("\n");
            content.append("请及时审核确认。");
            
            Message message = new Message();
            message.setFromId(elderUserId);
            message.setToId(contactUserId);
            message.setContent(content.toString());
            message.setMsgType("medicine_draft");
            message.setIsRead(0);
            messageMapper.insertMessage(message);
            
            Map<String, Object> notice = new HashMap<>();
            notice.put("msgType", "MEDICINE_DRAFT");
            notice.put("draftId", draft.getId());
            notice.put("elderId", elderUserId);
            notice.put("elderName", elderName);
            notice.put("medicineName", draft.getMedicineName());
            notice.put("confidence", draft.getConfidence());
            notice.put("content", content.toString());
            notice.put("inputMethod", inputMethodText);
            chatHandler.sendToUser(contactUserId, notice);
        }
    }
    
    private String getInputMethodText(String inputMethod) {
        if (inputMethod == null) return "手动录入";
        switch (inputMethod) {
            case MedicineDraft.INPUT_OCR: return "拍照识别";
            case MedicineDraft.INPUT_VOICE: return "语音录入";
            case MedicineDraft.INPUT_BARCODE: return "扫码录入";
            default: return "手动录入";
        }
    }

    public List<Map<String, Object>> getPendingDraftsForFamily(Long familyUserId) {
        List<MedicineDraft> drafts = draftMapper.selectPendingDraftsForFamily(familyUserId);
        return convertDraftsToMap(drafts);
    }
    
    public List<Map<String, Object>> getAllDraftsForFamily(Long familyUserId) {
        List<MedicineDraft> drafts = draftMapper.selectAllDraftsForFamily(familyUserId);
        return convertDraftsToMap(drafts);
    }
    
    private List<Map<String, Object>> convertDraftsToMap(List<MedicineDraft> drafts) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (MedicineDraft draft : drafts) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", draft.getId());
            item.put("elderUserId", draft.getElderUserId());
            item.put("elderName", draft.getElderName());
            item.put("elderAvatar", draft.getElderAvatar());
            item.put("status", draft.getStatus());
            item.put("operationType", draft.getOperationType() != null ? draft.getOperationType() : "create");
            item.put("targetMedicineId", draft.getTargetMedicineId());
            item.put("confidence", draft.getConfidence());
            item.put("medicineName", draft.getMedicineName());
            item.put("medicineType", draft.getMedicineType());
            item.put("spec", draft.getSpec());
            item.put("dosagePerTime", draft.getDosagePerTime());
            item.put("doseUnit", draft.getDoseUnit());
            item.put("frequencyPerDay", draft.getFrequencyPerDay());
            item.put("reminderTimes", parseReminderTimes(draft.getReminderTimes()));
            item.put("totalPackages", draft.getTotalPackages());
            item.put("packageQuantity", draft.getPackageQuantity());
            item.put("precautions", draft.getPrecautions());
            item.put("manufacturer", draft.getManufacturer());
            item.put("approvalNumber", draft.getApprovalNumber());
            item.put("coverImageUrl", draft.getCoverImageUrl());
            item.put("instructionImageUrl", draft.getInstructionImageUrl());
            item.put("voiceUrl", draft.getVoiceUrl());
            item.put("voiceText", draft.getVoiceText());
            item.put("rawOcrText", draft.getRawOcrText());
            item.put("inputMethod", draft.getInputMethod());
            item.put("inputMethodText", getInputMethodText(draft.getInputMethod()));
            item.put("barcode", draft.getBarcode());
            item.put("reviewNote", draft.getReviewNote());
            item.put("reviewTime", draft.getReviewTime());
            item.put("createTime", draft.getCreateTime());
            item.put("isExpired", draft.getIsExpired() != null && draft.getIsExpired());
            item.put("supersededBy", draft.getSupersededBy());
            item.put("familyName", draft.getFamilyName());
            result.add(item);
        }
        return result;
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

    public Map<String, Object> getDraftDetail(Long draftId) {
        MedicineDraft draft = draftMapper.selectById(draftId);
        if (draft == null) return null;
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", draft.getId());
        result.put("elderUserId", draft.getElderUserId());
        result.put("elderName", draft.getElderName());
        result.put("elderAvatar", draft.getElderAvatar());
        result.put("status", draft.getStatus());
        result.put("confidence", draft.getConfidence());
        result.put("medicineName", draft.getMedicineName());
        result.put("medicineType", draft.getMedicineType());
        result.put("spec", draft.getSpec());
        result.put("dosagePerTime", draft.getDosagePerTime());
        result.put("doseUnit", draft.getDoseUnit());
        result.put("frequencyPerDay", draft.getFrequencyPerDay());
        result.put("reminderTimes", parseReminderTimes(draft.getReminderTimes()));
        result.put("totalPackages", draft.getTotalPackages());
        result.put("packageQuantity", draft.getPackageQuantity());
        result.put("precautions", draft.getPrecautions());
        result.put("manufacturer", draft.getManufacturer());
        result.put("approvalNumber", draft.getApprovalNumber());
        result.put("coverImageUrl", draft.getCoverImageUrl());
        result.put("instructionImageUrl", draft.getInstructionImageUrl());
        result.put("voiceUrl", draft.getVoiceUrl());
        result.put("voiceText", draft.getVoiceText());
        result.put("rawOcrText", draft.getRawOcrText());
        result.put("inputMethod", draft.getInputMethod());
        result.put("inputMethodText", getInputMethodText(draft.getInputMethod()));
        result.put("barcode", draft.getBarcode());
        result.put("reviewNote", draft.getReviewNote());
        result.put("reviewTime", draft.getReviewTime());
        result.put("createTime", draft.getCreateTime());
        
        return result;
    }

    @Transactional
    public Result approveDraft(Long familyUserId, Long draftId, String reviewNote) {
        MedicineDraft draft = draftMapper.selectById(draftId);
        if (draft == null) {
            return Result.error("草稿不存在");
        }
        
        if (!MedicineDraft.STATUS_PENDING.equals(draft.getStatus())) {
            return Result.error("该草稿已被处理");
        }
        
        draftMapper.updateStatus(draftId, MedicineDraft.STATUS_APPROVED, familyUserId, reviewNote);
        
        String operationType = draft.getOperationType() != null ? draft.getOperationType() : "create";
        Object medicineId = null;
        String message = "";
        
        if (MedicineDraft.OP_DELETE.equals(operationType)) {
            if (draft.getTargetMedicineId() != null) {
                UserMedicine existingMedicine = userMedicineMapper.selectByIdIncludeDeleted(draft.getTargetMedicineId());
                if (existingMedicine != null) {
                    User familyUser = userMapper.getById(familyUserId);
                    String familyName = familyUser != null ? (familyUser.getNickname() != null ? familyUser.getNickname() : familyUser.getUsername()) : "子女";
                    
                    int totalStock = existingMedicine.getTotalStock() != null ? existingMedicine.getTotalStock() : 0;
                    int packageQuantity = existingMedicine.getPackageQuantity() != null ? existingMedicine.getPackageQuantity() : 0;
                    int totalPills = totalStock > 0 ? totalStock : (existingMedicine.getTotalPackages() != null ? existingMedicine.getTotalPackages() * packageQuantity : 0);
                    java.math.BigDecimal initialWeight = new java.math.BigDecimal(totalPills).multiply(new java.math.BigDecimal("1.0"));
                    
                    userMedicineMapper.softDelete(draft.getTargetMedicineId(), familyUserId, familyName, "子女", draft.getReviewNote());
                    
                    int unbindCount = unbindMedicineFromSlot(draft.getTargetMedicineId());
                    
                    clearStockAlerts(draft.getTargetMedicineId());
                    
                    clearMedicationLogs(draft.getTargetMedicineId());
                    
                    medicineId = draft.getTargetMedicineId();
                    message = "已确认删除该药品";
                    
                    Map<String, Object> deleteResult = new HashMap<>();
                    deleteResult.put("medicineId", medicineId);
                    deleteResult.put("medicineName", existingMedicine.getCustomName());
                    deleteResult.put("totalPills", totalPills);
                    deleteResult.put("initialWeight", initialWeight);
                    deleteResult.put("unbindCount", unbindCount);
                    
                    Map<String, Object> result = new HashMap<>();
                    result.put("medicineId", medicineId);
                    result.put("message", message);
                    result.put("deleteResult", deleteResult);
                    return Result.success(result);
                }
            }
            message = "已确认删除该药品";
        } else if (MedicineDraft.OP_UPDATE.equals(operationType)) {
            if (draft.getTargetMedicineId() != null) {
                UserMedicine existingMedicine = userMedicineMapper.selectByIdIncludeDeleted(draft.getTargetMedicineId());
                if (existingMedicine != null) {
                    if (draft.getMedicineName() != null) {
                        existingMedicine.setCustomName(draft.getMedicineName());
                    }
                    if (draft.getMedicineType() != null) {
                        existingMedicine.setType(draft.getMedicineType());
                    }
                    if (draft.getSpec() != null) {
                        existingMedicine.setSpec(draft.getSpec());
                    }
                    if (draft.getDosagePerTime() != null) {
                        existingMedicine.setDosagePerTime(draft.getDosagePerTime());
                    }
                    if (draft.getDoseUnit() != null) {
                        existingMedicine.setDoseUnit(draft.getDoseUnit());
                    }
                    if (draft.getFrequencyPerDay() != null) {
                        existingMedicine.setFrequencyPerDay(draft.getFrequencyPerDay());
                    }
                    if (draft.getReminderTimes() != null) {
                        existingMedicine.setReminderTimes(draft.getReminderTimes());
                    }
                    if (draft.getTotalPackages() != null) {
                        existingMedicine.setTotalPackages(draft.getTotalPackages());
                    }
                    if (draft.getPackageQuantity() != null) {
                        existingMedicine.setPackageQuantity(draft.getPackageQuantity());
                    }
                    
                    int newTotalStock = existingMedicine.getTotalPackages() * existingMedicine.getPackageQuantity();
                    int usedStock = existingMedicine.getTotalStock() - existingMedicine.getCurrentStock();
                    existingMedicine.setTotalStock(newTotalStock);
                    existingMedicine.setCurrentStock(Math.max(0, newTotalStock - usedStock));
                    
                    userMedicineMapper.update(existingMedicine);
                    
                    syncMedicineBoxSlot(existingMedicine.getId(), existingMedicine.getCurrentStock(), existingMedicine.getDosagePerTime());
                    
                    medicineId = existingMedicine.getId();
                }
            }
            message = "已确认修改药品信息";
        } else {
            UserMedicine medicine = createMedicineFromDraft(draft);
            medicineId = medicine.getId();
            message = "审核通过，已添加到老人用药列表";
        }
        
        DraftReviewLog log = new DraftReviewLog();
        log.setDraftId(draftId);
        log.setReviewerId(familyUserId);
        log.setReviewerName(getUserName(familyUserId));
        log.setElderId(draft.getElderUserId());
        log.setElderName(draft.getElderName());
        log.setAction(DraftReviewLog.ACTION_APPROVE);
        log.setReviewNote(reviewNote);
        reviewLogMapper.insert(log);
        
        notifyElderDraftApproved(draft.getElderUserId(), draft.getMedicineName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("medicineId", medicineId);
        result.put("message", message);
        return Result.success(result);
    }

    @Transactional
    public Result approveDraftWithEdit(Long familyUserId, Long draftId, 
                                        Map<String, Object> modifiedData, String reviewNote) {
        MedicineDraft draft = draftMapper.selectById(draftId);
        if (draft == null) {
            return Result.error("草稿不存在");
        }
        
        if (!MedicineDraft.STATUS_PENDING.equals(draft.getStatus())) {
            return Result.error("该草稿已被处理");
        }
        
        String originalData = JSON.toJSONString(convertDraftToMap(draft));
        
        if (modifiedData.get("medicineName") != null) {
            draft.setMedicineName((String) modifiedData.get("medicineName"));
        }
        if (modifiedData.get("medicineType") != null) {
            draft.setMedicineType((String) modifiedData.get("medicineType"));
        }
        if (modifiedData.get("spec") != null) {
            draft.setSpec((String) modifiedData.get("spec"));
        }
        if (modifiedData.get("dosagePerTime") != null) {
            Object obj = modifiedData.get("dosagePerTime");
            draft.setDosagePerTime(obj instanceof Number ? new BigDecimal(obj.toString()) : new BigDecimal("1"));
        }
        if (modifiedData.get("doseUnit") != null) {
            draft.setDoseUnit((String) modifiedData.get("doseUnit"));
        }
        if (modifiedData.get("frequencyPerDay") != null) {
            Object obj = modifiedData.get("frequencyPerDay");
            draft.setFrequencyPerDay(obj instanceof Number ? ((Number) obj).intValue() : 2);
        }
        if (modifiedData.get("reminderTimes") != null) {
            List<String> times = (List<String>) modifiedData.get("reminderTimes");
            draft.setReminderTimes(JSON.toJSONString(times));
        }
        if (modifiedData.get("totalPackages") != null) {
            Object obj = modifiedData.get("totalPackages");
            draft.setTotalPackages(obj instanceof Number ? ((Number) obj).intValue() : 1);
        }
        if (modifiedData.get("packageQuantity") != null) {
            Object obj = modifiedData.get("packageQuantity");
            draft.setPackageQuantity(obj instanceof Number ? ((Number) obj).intValue() : 30);
        }
        if (modifiedData.get("precautions") != null) {
            draft.setPrecautions((String) modifiedData.get("precautions"));
        }
        
        draftMapper.updateMedicineInfo(draft);
        draftMapper.updateStatus(draftId, MedicineDraft.STATUS_APPROVED, familyUserId, reviewNote);
        
        Long medicineId = null;
        String message;
        
        if (MedicineDraft.OP_UPDATE.equals(draft.getOperationType()) && draft.getTargetMedicineId() != null) {
            UserMedicine existingMedicine = userMedicineMapper.selectByIdIncludeDeleted(draft.getTargetMedicineId());
            if (existingMedicine != null) {
                if (draft.getMedicineName() != null) {
                    existingMedicine.setCustomName(draft.getMedicineName());
                }
                if (draft.getMedicineType() != null) {
                    existingMedicine.setType(draft.getMedicineType());
                }
                if (draft.getSpec() != null) {
                    existingMedicine.setSpec(draft.getSpec());
                }
                if (draft.getDosagePerTime() != null) {
                    existingMedicine.setDosagePerTime(draft.getDosagePerTime());
                }
                if (draft.getDoseUnit() != null) {
                    existingMedicine.setDoseUnit(draft.getDoseUnit());
                }
                if (draft.getFrequencyPerDay() != null) {
                    existingMedicine.setFrequencyPerDay(draft.getFrequencyPerDay());
                }
                if (draft.getReminderTimes() != null) {
                    existingMedicine.setReminderTimes(draft.getReminderTimes());
                }
                if (draft.getTotalPackages() != null) {
                    existingMedicine.setTotalPackages(draft.getTotalPackages());
                }
                if (draft.getPackageQuantity() != null) {
                    existingMedicine.setPackageQuantity(draft.getPackageQuantity());
                }
                
                int newTotalStock = existingMedicine.getTotalPackages() * existingMedicine.getPackageQuantity();
                int usedStock = existingMedicine.getTotalStock() - existingMedicine.getCurrentStock();
                existingMedicine.setTotalStock(newTotalStock);
                existingMedicine.setCurrentStock(Math.max(0, newTotalStock - usedStock));
                
                userMedicineMapper.update(existingMedicine);
                
                syncMedicineBoxSlot(existingMedicine.getId(), existingMedicine.getCurrentStock(), existingMedicine.getDosagePerTime());
                
                medicineId = existingMedicine.getId().longValue();
                message = "修改后审核通过，药品信息已更新";
            } else {
                UserMedicine medicine = createMedicineFromDraft(draft);
                medicineId = medicine.getId().longValue();
                message = "修改后审核通过，已添加到老人用药列表";
            }
        } else if (MedicineDraft.OP_DELETE.equals(draft.getOperationType())) {
            if (draft.getTargetMedicineId() != null) {
                UserMedicine existingMedicine = userMedicineMapper.selectByIdIncludeDeleted(draft.getTargetMedicineId());
                if (existingMedicine != null) {
                    User familyUser = userMapper.getById(familyUserId);
                    String familyName = familyUser != null ? (familyUser.getNickname() != null ? familyUser.getNickname() : familyUser.getUsername()) : "子女";
                    userMedicineMapper.softDelete(draft.getTargetMedicineId(), familyUserId, familyName, "子女", reviewNote);
                    unbindMedicineFromSlot(draft.getTargetMedicineId());
                    clearStockAlerts(draft.getTargetMedicineId());
                    clearMedicationLogs(draft.getTargetMedicineId());
                    medicineId = draft.getTargetMedicineId().longValue();
                }
            }
            message = "已确认删除该药品";
        } else {
            UserMedicine medicine = createMedicineFromDraft(draft);
            medicineId = medicine.getId().longValue();
            message = "修改后审核通过，已添加到老人用药列表";
        }
        
        DraftReviewLog log = new DraftReviewLog();
        log.setDraftId(draftId);
        log.setReviewerId(familyUserId);
        log.setReviewerName(getUserName(familyUserId));
        log.setElderId(draft.getElderUserId());
        log.setElderName(draft.getElderName());
        log.setAction(DraftReviewLog.ACTION_APPROVE_WITH_EDIT);
        log.setOriginalData(originalData);
        log.setModifiedData(JSON.toJSONString(modifiedData));
        log.setReviewNote(reviewNote);
        reviewLogMapper.insert(log);
        
        notifyElderDraftApproved(draft.getElderUserId(), draft.getMedicineName());
        
        Map<String, Object> result = new HashMap<>();
        result.put("medicineId", medicineId);
        result.put("message", message);
        return Result.success(result);
    }

    @Transactional
    public Result rejectDraft(Long familyUserId, Long draftId, String reviewNote) {
        MedicineDraft draft = draftMapper.selectById(draftId);
        if (draft == null) {
            return Result.error("草稿不存在");
        }
        
        if (!MedicineDraft.STATUS_PENDING.equals(draft.getStatus())) {
            return Result.error("该草稿已被处理");
        }
        
        draftMapper.updateStatus(draftId, MedicineDraft.STATUS_REJECTED, familyUserId, reviewNote);
        
        DraftReviewLog log = new DraftReviewLog();
        log.setDraftId(draftId);
        log.setReviewerId(familyUserId);
        log.setReviewerName(getUserName(familyUserId));
        log.setElderId(draft.getElderUserId());
        log.setElderName(draft.getElderName());
        log.setAction(DraftReviewLog.ACTION_REJECT);
        log.setReviewNote(reviewNote);
        reviewLogMapper.insert(log);
        
        notifyElderDraftRejected(draft.getElderUserId(), draft.getMedicineName(), reviewNote);
        
        return Result.success("已驳回草稿");
    }

    @Transactional
    public Result deleteDraft(Long familyUserId, Long draftId, String reviewNote) {
        MedicineDraft draft = draftMapper.selectById(draftId);
        if (draft == null) {
            return Result.error("草稿不存在");
        }
        
        if (!MedicineDraft.STATUS_PENDING.equals(draft.getStatus())) {
            return Result.error("该草稿已被处理");
        }
        
        draftMapper.updateStatus(draftId, MedicineDraft.STATUS_DELETED, familyUserId, reviewNote);
        
        DraftReviewLog log = new DraftReviewLog();
        log.setDraftId(draftId);
        log.setReviewerId(familyUserId);
        log.setReviewerName(getUserName(familyUserId));
        log.setElderId(draft.getElderUserId());
        log.setElderName(draft.getElderName());
        log.setAction(DraftReviewLog.ACTION_DELETE);
        log.setReviewNote(reviewNote);
        reviewLogMapper.insert(log);
        
        notifyElderDraftDeleted(draft.getElderUserId(), draft.getMedicineName());
        
        return Result.success("已删除草稿");
    }
    
    private UserMedicine createMedicineFromDraft(MedicineDraft draft) {
        UserMedicine medicine = new UserMedicine();
        medicine.setUserId(draft.getElderUserId().intValue());
        medicine.setCustomName(draft.getMedicineName());
        medicine.setType(draft.getMedicineType() != null ? draft.getMedicineType() : "其他");
        medicine.setSpec(draft.getSpec());
        medicine.setDosagePerTime(draft.getDosagePerTime() != null ? draft.getDosagePerTime() : new BigDecimal("1"));
        medicine.setDoseUnit(draft.getDoseUnit() != null ? draft.getDoseUnit() : "片");
        medicine.setFrequencyPerDay(draft.getFrequencyPerDay() != null ? draft.getFrequencyPerDay() : 2);
        medicine.setReminderTimes(draft.getReminderTimes() != null ? draft.getReminderTimes() : "[\"08:00\",\"18:00\"]");
        medicine.setTotalPackages(draft.getTotalPackages() != null ? draft.getTotalPackages() : 1);
        medicine.setPackageQuantity(draft.getPackageQuantity() != null ? draft.getPackageQuantity() : 30);
        
        int totalStock = medicine.getTotalPackages() * medicine.getPackageQuantity();
        medicine.setTotalStock(totalStock);
        medicine.setCurrentStock(totalStock);
        medicine.setThresholdDays(2);
        medicine.setStatus(1);
        medicine.setStartDate(java.time.LocalDate.now());
        
        userMedicineMapper.insert(medicine);
        
        generateMedicationLogs(medicine.getId(), parseReminderTimes(medicine.getReminderTimes()));
        
        return medicine;
    }
    
    private void generateMedicationLogs(Integer userMedicineId, List<String> reminderTimes) {
        java.time.LocalDate today = java.time.LocalDate.now();
        java.time.LocalDate endDate = today.plusDays(7);
        
        for (java.time.LocalDate date = today; date.isBefore(endDate); date = date.plusDays(1)) {
            for (String timeStr : reminderTimes) {
                try {
                    java.time.LocalTime time = java.time.LocalTime.parse(timeStr, 
                        java.time.format.DateTimeFormatter.ofPattern("HH:mm"));
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
    
    private void notifyElderDraftApproved(Long elderUserId, String medicineName) {
        String content = "【用药信息已确认】\n家人已确认您的用药信息「" + medicineName + "」，可查看详情～";
        
        Map<String, Object> notice = new HashMap<>();
        notice.put("msgType", "MEDICINE_APPROVED");
        notice.put("medicineName", medicineName);
        notice.put("content", content);
        chatHandler.sendToUser(elderUserId, notice);
    }
    
    private void notifyElderDraftRejected(Long elderUserId, String medicineName, String reason) {
        String content = "【用药信息需要修改】\n家人说「" + medicineName + "」的用药信息需要修改";
        if (reason != null && !reason.isEmpty()) {
            content += "，原因：" + reason;
        }
        content += "。您可以重新拍照/口述录入哦～";
        
        Map<String, Object> notice = new HashMap<>();
        notice.put("msgType", "MEDICINE_REJECTED");
        notice.put("medicineName", medicineName);
        notice.put("reason", reason);
        notice.put("content", content);
        chatHandler.sendToUser(elderUserId, notice);
    }
    
    private void notifyElderDraftDeleted(Long elderUserId, String medicineName) {
        String content = "【用药信息已删除】\n家人已删除「" + medicineName + "」的用药信息草稿，如需添加请重新录入。";
        
        Map<String, Object> notice = new HashMap<>();
        notice.put("msgType", "MEDICINE_DELETED");
        notice.put("medicineName", medicineName);
        notice.put("content", content);
        chatHandler.sendToUser(elderUserId, notice);
    }
    
    private Map<String, Object> convertDraftToMap(MedicineDraft draft) {
        Map<String, Object> map = new HashMap<>();
        map.put("medicineName", draft.getMedicineName());
        map.put("medicineType", draft.getMedicineType());
        map.put("spec", draft.getSpec());
        map.put("dosagePerTime", draft.getDosagePerTime());
        map.put("doseUnit", draft.getDoseUnit());
        map.put("frequencyPerDay", draft.getFrequencyPerDay());
        map.put("reminderTimes", draft.getReminderTimes());
        map.put("totalPackages", draft.getTotalPackages());
        map.put("packageQuantity", draft.getPackageQuantity());
        map.put("precautions", draft.getPrecautions());
        return map;
    }

    public List<Map<String, Object>> getDraftsByElder(Long elderUserId) {
        List<MedicineDraft> drafts = draftMapper.selectByElderUserId(elderUserId);
        return convertDraftsToMap(drafts);
    }
    
    public List<Map<String, Object>> getPendingDraftsByElder(Long elderUserId) {
        List<MedicineDraft> drafts = draftMapper.selectByElderUserIdAndStatus(elderUserId, MedicineDraft.STATUS_PENDING);
        return convertDraftsToMap(drafts);
    }
    
    public int getPendingDraftCountForFamily(Long familyUserId) {
        return draftMapper.countPendingDraftsForFamily(familyUserId);
    }
    
    public int getPendingDraftCountByElder(Long elderUserId) {
        return draftMapper.countPendingByElderUserId(elderUserId);
    }
    
    @Transactional
    public Result createUpdateDraft(Long elderUserId, Integer medicineId, Map<String, Object> params) {
        UserMedicine medicine = userMedicineMapper.selectByIdIncludeDeleted(medicineId);
        if (medicine == null) {
            return Result.error("药品不存在");
        }
        
        MedicineDraft draft = new MedicineDraft();
        draft.setElderUserId(elderUserId);
        draft.setStatus(MedicineDraft.STATUS_PENDING);
        draft.setOperationType(MedicineDraft.OP_UPDATE);
        draft.setTargetMedicineId(medicineId);
        
        draft.setMedicineName((String) params.getOrDefault("name", medicine.getCustomName() != null ? medicine.getCustomName() : medicine.getMedicineName()));
        draft.setMedicineType((String) params.getOrDefault("type", medicine.getType()));
        draft.setSpec((String) params.getOrDefault("spec", medicine.getSpec()));
        
        if (params.get("dosagePerTime") != null) {
            Object obj = params.get("dosagePerTime");
            draft.setDosagePerTime(obj instanceof Number ? new BigDecimal(obj.toString()) : medicine.getDosagePerTime());
        } else {
            draft.setDosagePerTime(medicine.getDosagePerTime());
        }
        
        String doseUnit = (String) params.getOrDefault("unit", medicine.getDoseUnit());
        draft.setDoseUnit(doseUnit != null ? doseUnit : medicine.getDoseUnit());
        
        Object freqObj = params.get("frequency");
        if (freqObj != null) {
            draft.setFrequencyPerDay(freqObj instanceof Number ? ((Number) freqObj).intValue() : medicine.getFrequencyPerDay());
        } else {
            draft.setFrequencyPerDay(medicine.getFrequencyPerDay());
        }
        
        List<String> reminderTimes = (List<String>) params.get("reminderTimes");
        if (reminderTimes != null && !reminderTimes.isEmpty()) {
            draft.setReminderTimes(JSON.toJSONString(reminderTimes));
        } else {
            draft.setReminderTimes(medicine.getReminderTimes());
        }
        
        if (params.get("totalPackages") != null) {
            Object obj = params.get("totalPackages");
            draft.setTotalPackages(obj instanceof Number ? ((Number) obj).intValue() : medicine.getTotalPackages());
        } else {
            draft.setTotalPackages(medicine.getTotalPackages());
        }
        
        if (params.get("packageQuantity") != null) {
            Object obj = params.get("packageQuantity");
            draft.setPackageQuantity(obj instanceof Number ? ((Number) obj).intValue() : medicine.getPackageQuantity());
        } else {
            draft.setPackageQuantity(medicine.getPackageQuantity());
        }
        
        draft.setInputMethod(MedicineDraft.INPUT_MANUAL);
        
        draftMapper.insert(draft);
        
        draftMapper.markPreviousDraftsAsExpired(elderUserId, medicineId, MedicineDraft.OP_UPDATE, draft.getId());
        
        notifyFamilyMembersForUpdate(elderUserId, draft, medicine);
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", draft.getId());
        result.put("message", "修改申请已提交，等待家人确认");
        
        return Result.success(result);
    }
    
    @Transactional
    public Result createDeleteDraft(Long elderUserId, Integer medicineId, String reason) {
        UserMedicine medicine = userMedicineMapper.selectByIdIncludeDeleted(medicineId);
        if (medicine == null) {
            return Result.error("药品不存在");
        }
        
        MedicineDraft draft = new MedicineDraft();
        draft.setElderUserId(elderUserId);
        draft.setStatus(MedicineDraft.STATUS_PENDING);
        draft.setOperationType(MedicineDraft.OP_DELETE);
        draft.setTargetMedicineId(medicineId);
        
        String medName = medicine.getCustomName() != null ? medicine.getCustomName() : medicine.getMedicineName();
        draft.setMedicineName(medName);
        draft.setMedicineType(medicine.getType());
        draft.setSpec(medicine.getSpec());
        draft.setDosagePerTime(medicine.getDosagePerTime());
        draft.setDoseUnit(medicine.getDoseUnit());
        draft.setFrequencyPerDay(medicine.getFrequencyPerDay());
        draft.setReminderTimes(medicine.getReminderTimes());
        draft.setTotalPackages(medicine.getTotalPackages());
        draft.setPackageQuantity(medicine.getPackageQuantity());
        draft.setInputMethod(MedicineDraft.INPUT_MANUAL);
        draft.setReviewNote(reason);
        
        draftMapper.insert(draft);
        
        draftMapper.markPreviousDraftsAsExpired(elderUserId, medicineId, MedicineDraft.OP_DELETE, draft.getId());
        
        notifyFamilyMembersForDelete(elderUserId, draft, medicine, reason);
        
        Map<String, Object> result = new HashMap<>();
        result.put("id", draft.getId());
        result.put("message", "删除申请已提交，等待家人确认");
        
        return Result.success(result);
    }
    
    private void notifyFamilyMembersForUpdate(Long elderUserId, MedicineDraft draft, UserMedicine medicine) {
        User elder = userMapper.getById(elderUserId);
        if (elder == null) return;
        
        String elderName = elder.getNickname() != null ? elder.getNickname() : elder.getUsername();
        
        List<EmergencyContact> emergencyContacts = emergencyContactMapper.selectByUserIdAndReviewType(elderUserId, EmergencyContact.REVIEW_TYPE_MEDICINE);
        if (emergencyContacts == null || emergencyContacts.isEmpty()) {
            emergencyContacts = emergencyContactMapper.selectByUserId(elderUserId);
        }
        if (emergencyContacts == null || emergencyContacts.isEmpty()) return;
        
        for (EmergencyContact contact : emergencyContacts) {
            Long contactUserId = contact.getContactUserId();
            if (contactUserId == null) continue;
            
            StringBuilder content = new StringBuilder();
            content.append("【用药修改待审核】\n");
            content.append(elderName).append("申请修改药品信息，请核对！\n\n");
            content.append("药品名称：").append(draft.getMedicineName()).append("\n");
            if (draft.getSpec() != null) {
                content.append("药品规格：").append(draft.getSpec()).append("\n");
            }
            if (draft.getDosagePerTime() != null) {
                content.append("每次用量：").append(draft.getDosagePerTime())
                       .append(draft.getDoseUnit() != null ? draft.getDoseUnit() : "片").append("\n");
            }
            if (draft.getFrequencyPerDay() != null) {
                content.append("每日次数：").append(draft.getFrequencyPerDay()).append("次\n");
            }
            content.append("\n请及时审核确认。");
            
            Message message = new Message();
            message.setFromId(elderUserId);
            message.setToId(contactUserId);
            message.setContent(content.toString());
            message.setMsgType("medicine_draft");
            message.setIsRead(0);
            messageMapper.insertMessage(message);
            
            Map<String, Object> notice = new HashMap<>();
            notice.put("msgType", "MEDICINE_DRAFT");
            notice.put("draftId", draft.getId());
            notice.put("elderId", elderUserId);
            notice.put("elderName", elderName);
            notice.put("medicineName", draft.getMedicineName());
            notice.put("operationType", "update");
            notice.put("content", content.toString());
            chatHandler.sendToUser(contactUserId, notice);
        }
    }
    
    private void notifyFamilyMembersForDelete(Long elderUserId, MedicineDraft draft, UserMedicine medicine, String reason) {
        User elder = userMapper.getById(elderUserId);
        if (elder == null) return;
        
        String elderName = elder.getNickname() != null ? elder.getNickname() : elder.getUsername();
        
        List<EmergencyContact> emergencyContacts = emergencyContactMapper.selectByUserIdAndReviewType(elderUserId, EmergencyContact.REVIEW_TYPE_MEDICINE);
        if (emergencyContacts == null || emergencyContacts.isEmpty()) {
            emergencyContacts = emergencyContactMapper.selectByUserId(elderUserId);
        }
        if (emergencyContacts == null || emergencyContacts.isEmpty()) return;
        
        for (EmergencyContact contact : emergencyContacts) {
            Long contactUserId = contact.getContactUserId();
            if (contactUserId == null) continue;
            
            StringBuilder content = new StringBuilder();
            content.append("【用药删除待审核】\n");
            content.append(elderName).append("申请删除药品信息，请确认！\n\n");
            content.append("药品名称：").append(draft.getMedicineName()).append("\n");
            if (draft.getSpec() != null) {
                content.append("药品规格：").append(draft.getSpec()).append("\n");
            }
            if (reason != null && !reason.isEmpty()) {
                content.append("删除原因：").append(reason).append("\n");
            }
            content.append("\n请及时审核确认。");
            
            Message message = new Message();
            message.setFromId(elderUserId);
            message.setToId(contactUserId);
            message.setContent(content.toString());
            message.setMsgType("medicine_draft");
            message.setIsRead(0);
            messageMapper.insertMessage(message);
            
            Map<String, Object> notice = new HashMap<>();
            notice.put("msgType", "MEDICINE_DRAFT");
            notice.put("draftId", draft.getId());
            notice.put("elderId", elderUserId);
            notice.put("elderName", elderName);
            notice.put("medicineName", draft.getMedicineName());
            notice.put("operationType", "delete");
            notice.put("reason", reason);
            notice.put("content", content.toString());
            chatHandler.sendToUser(contactUserId, notice);
        }
    }
    
    private int unbindMedicineFromSlot(Integer medicineId) {
        try {
            return jdbcTemplate.update(
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
        } catch (Exception e) {
            return 0;
        }
    }
    
    private void clearStockAlerts(Integer medicineId) {
        try {
            int cleared = jdbcTemplate.update(
                "DELETE FROM medicine_stock_alert WHERE user_medicine_id = ?",
                medicineId
            );
            System.out.println("[库存预警] 已清除药品 " + medicineId + " 的 " + cleared + " 条预警");
        } catch (Exception e) {
            System.out.println("[库存预警] 清除失败: " + e.getMessage());
        }
    }
    
    private void clearMedicationLogs(Integer medicineId) {
        try {
            String today = java.time.LocalDate.now().toString();
            int cleared = jdbcTemplate.update(
                "DELETE FROM medication_logs WHERE user_medicine_id = ? AND DATE(scheduled_time) >= ?",
                medicineId, today
            );
            System.out.println("[服药日志] 已清除药品 " + medicineId + " 的 " + cleared + " 条未来服药记录（保留历史记录）");
        } catch (Exception e) {
            System.out.println("[服药日志] 清除失败: " + e.getMessage());
        }
    }
    
    private void syncMedicineBoxSlot(Integer medicineId, int currentStock, java.math.BigDecimal dosagePerTime) {
        try {
            java.math.BigDecimal currentWeight = new java.math.BigDecimal(currentStock);
            java.math.BigDecimal dosageWeight = dosagePerTime != null ? dosagePerTime : new java.math.BigDecimal("1");
            
            int updated = jdbcTemplate.update(
                "UPDATE medicine_box_slot SET current_weight = ?, dosage_weight = ?, update_time = NOW() WHERE user_medicine_id = ?",
                currentWeight, dosageWeight, medicineId
            );
            
            if (updated > 0) {
                System.out.println("[药仓同步] 药品 " + medicineId + " 已同步到药仓，库存: " + currentStock + "，剂量: " + dosageWeight);
            }
        } catch (Exception e) {
            System.out.println("[药仓同步] 同步失败: " + e.getMessage());
        }
    }
    
    private String getUserName(Long userId) {
        if (userId == null) return "未知";
        User user = userMapper.getById(userId);
        if (user == null) return "未知";
        return user.getNickname() != null ? user.getNickname() : user.getUsername();
    }
}

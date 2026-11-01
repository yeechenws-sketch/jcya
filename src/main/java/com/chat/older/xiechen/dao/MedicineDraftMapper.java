package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.MedicineDraft;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface MedicineDraftMapper {

    @Select("SELECT d.*, u.nickname as elderName, u.avatar as elderAvatar, f.nickname as familyName " +
            "FROM medicine_draft d " +
            "LEFT JOIN users u ON d.elder_user_id = u.id " +
            "LEFT JOIN users f ON d.family_user_id = f.id " +
            "WHERE d.id = #{id}")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "elderUserId", column = "elder_user_id"),
            @Result(property = "familyUserId", column = "family_user_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "operationType", column = "operation_type"),
            @Result(property = "targetMedicineId", column = "target_medicine_id"),
            @Result(property = "confidence", column = "confidence"),
            @Result(property = "medicineName", column = "medicine_name"),
            @Result(property = "medicineType", column = "medicine_type"),
            @Result(property = "spec", column = "spec"),
            @Result(property = "dosagePerTime", column = "dosage_per_time"),
            @Result(property = "doseUnit", column = "dose_unit"),
            @Result(property = "frequencyPerDay", column = "frequency_per_day"),
            @Result(property = "reminderTimes", column = "reminder_times"),
            @Result(property = "totalPackages", column = "total_packages"),
            @Result(property = "packageQuantity", column = "package_quantity"),
            @Result(property = "precautions", column = "precautions"),
            @Result(property = "manufacturer", column = "manufacturer"),
            @Result(property = "approvalNumber", column = "approval_number"),
            @Result(property = "coverImageUrl", column = "cover_image_url"),
            @Result(property = "instructionImageUrl", column = "instruction_image_url"),
            @Result(property = "voiceUrl", column = "voice_url"),
            @Result(property = "voiceText", column = "voice_text"),
            @Result(property = "rawOcrText", column = "raw_ocr_text"),
            @Result(property = "inputMethod", column = "input_method"),
            @Result(property = "barcode", column = "barcode"),
            @Result(property = "reviewTime", column = "review_time"),
            @Result(property = "reviewNote", column = "review_note"),
            @Result(property = "isExpired", column = "is_expired"),
            @Result(property = "supersededBy", column = "superseded_by"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "elderName", column = "elderName"),
            @Result(property = "elderAvatar", column = "elderAvatar"),
            @Result(property = "familyName", column = "familyName")
    })
    MedicineDraft selectById(@Param("id") Long id);

    @Select("SELECT d.*, u.nickname as elderName, u.avatar as elderAvatar " +
            "FROM medicine_draft d " +
            "LEFT JOIN users u ON d.elder_user_id = u.id " +
            "WHERE d.elder_user_id = #{elderUserId} " +
            "ORDER BY d.create_time DESC")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "elderUserId", column = "elder_user_id"),
            @Result(property = "familyUserId", column = "family_user_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "operationType", column = "operation_type"),
            @Result(property = "targetMedicineId", column = "target_medicine_id"),
            @Result(property = "confidence", column = "confidence"),
            @Result(property = "medicineName", column = "medicine_name"),
            @Result(property = "medicineType", column = "medicine_type"),
            @Result(property = "spec", column = "spec"),
            @Result(property = "dosagePerTime", column = "dosage_per_time"),
            @Result(property = "doseUnit", column = "dose_unit"),
            @Result(property = "frequencyPerDay", column = "frequency_per_day"),
            @Result(property = "reminderTimes", column = "reminder_times"),
            @Result(property = "totalPackages", column = "total_packages"),
            @Result(property = "packageQuantity", column = "package_quantity"),
            @Result(property = "precautions", column = "precautions"),
            @Result(property = "manufacturer", column = "manufacturer"),
            @Result(property = "approvalNumber", column = "approval_number"),
            @Result(property = "coverImageUrl", column = "cover_image_url"),
            @Result(property = "instructionImageUrl", column = "instruction_image_url"),
            @Result(property = "voiceUrl", column = "voice_url"),
            @Result(property = "voiceText", column = "voice_text"),
            @Result(property = "rawOcrText", column = "raw_ocr_text"),
            @Result(property = "inputMethod", column = "input_method"),
            @Result(property = "barcode", column = "barcode"),
            @Result(property = "reviewTime", column = "review_time"),
            @Result(property = "reviewNote", column = "review_note"),
            @Result(property = "isExpired", column = "is_expired"),
            @Result(property = "supersededBy", column = "superseded_by"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "elderName", column = "elderName"),
            @Result(property = "elderAvatar", column = "elderAvatar")
    })
    List<MedicineDraft> selectByElderUserId(@Param("elderUserId") Long elderUserId);

    @Select("SELECT d.*, u.nickname as elderName, u.avatar as elderAvatar " +
            "FROM medicine_draft d " +
            "LEFT JOIN users u ON d.elder_user_id = u.id " +
            "WHERE d.elder_user_id = #{elderUserId} AND d.status = #{status} " +
            "ORDER BY d.create_time DESC")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "elderUserId", column = "elder_user_id"),
            @Result(property = "familyUserId", column = "family_user_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "operationType", column = "operation_type"),
            @Result(property = "targetMedicineId", column = "target_medicine_id"),
            @Result(property = "confidence", column = "confidence"),
            @Result(property = "medicineName", column = "medicine_name"),
            @Result(property = "medicineType", column = "medicine_type"),
            @Result(property = "spec", column = "spec"),
            @Result(property = "dosagePerTime", column = "dosage_per_time"),
            @Result(property = "doseUnit", column = "dose_unit"),
            @Result(property = "frequencyPerDay", column = "frequency_per_day"),
            @Result(property = "reminderTimes", column = "reminder_times"),
            @Result(property = "totalPackages", column = "total_packages"),
            @Result(property = "packageQuantity", column = "package_quantity"),
            @Result(property = "precautions", column = "precautions"),
            @Result(property = "manufacturer", column = "manufacturer"),
            @Result(property = "approvalNumber", column = "approval_number"),
            @Result(property = "coverImageUrl", column = "cover_image_url"),
            @Result(property = "instructionImageUrl", column = "instruction_image_url"),
            @Result(property = "voiceUrl", column = "voice_url"),
            @Result(property = "voiceText", column = "voice_text"),
            @Result(property = "rawOcrText", column = "raw_ocr_text"),
            @Result(property = "inputMethod", column = "input_method"),
            @Result(property = "barcode", column = "barcode"),
            @Result(property = "reviewTime", column = "review_time"),
            @Result(property = "reviewNote", column = "review_note"),
            @Result(property = "isExpired", column = "is_expired"),
            @Result(property = "supersededBy", column = "superseded_by"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "elderName", column = "elderName"),
            @Result(property = "elderAvatar", column = "elderAvatar")
    })
    List<MedicineDraft> selectByElderUserIdAndStatus(@Param("elderUserId") Long elderUserId, @Param("status") String status);

    @Select("SELECT d.*, u.nickname as elderName, u.avatar as elderAvatar, f.nickname as familyName " +
            "FROM medicine_draft d " +
            "LEFT JOIN users u ON d.elder_user_id = u.id " +
            "LEFT JOIN users f ON d.family_user_id = f.id " +
            "WHERE d.elder_user_id IN " +
            "(SELECT friend_id FROM friendships WHERE user_id = #{familyUserId} AND status = 1) " +
            "AND d.status = 'pending' AND (d.is_expired IS NULL OR d.is_expired = 0) " +
            "ORDER BY d.create_time DESC")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "elderUserId", column = "elder_user_id"),
            @Result(property = "familyUserId", column = "family_user_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "operationType", column = "operation_type"),
            @Result(property = "targetMedicineId", column = "target_medicine_id"),
            @Result(property = "confidence", column = "confidence"),
            @Result(property = "medicineName", column = "medicine_name"),
            @Result(property = "medicineType", column = "medicine_type"),
            @Result(property = "spec", column = "spec"),
            @Result(property = "dosagePerTime", column = "dosage_per_time"),
            @Result(property = "doseUnit", column = "dose_unit"),
            @Result(property = "frequencyPerDay", column = "frequency_per_day"),
            @Result(property = "reminderTimes", column = "reminder_times"),
            @Result(property = "totalPackages", column = "total_packages"),
            @Result(property = "packageQuantity", column = "package_quantity"),
            @Result(property = "precautions", column = "precautions"),
            @Result(property = "manufacturer", column = "manufacturer"),
            @Result(property = "approvalNumber", column = "approval_number"),
            @Result(property = "coverImageUrl", column = "cover_image_url"),
            @Result(property = "instructionImageUrl", column = "instruction_image_url"),
            @Result(property = "voiceUrl", column = "voice_url"),
            @Result(property = "voiceText", column = "voice_text"),
            @Result(property = "rawOcrText", column = "raw_ocr_text"),
            @Result(property = "inputMethod", column = "input_method"),
            @Result(property = "barcode", column = "barcode"),
            @Result(property = "reviewTime", column = "review_time"),
            @Result(property = "reviewNote", column = "review_note"),
            @Result(property = "isExpired", column = "is_expired"),
            @Result(property = "supersededBy", column = "superseded_by"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "elderName", column = "elderName"),
            @Result(property = "elderAvatar", column = "elderAvatar"),
            @Result(property = "familyName", column = "familyName")
    })
    List<MedicineDraft> selectPendingDraftsForFamily(@Param("familyUserId") Long familyUserId);

    @Select("SELECT COUNT(*) FROM medicine_draft d " +
            "WHERE d.elder_user_id IN " +
            "(SELECT friend_id FROM friendships WHERE user_id = #{familyUserId} AND status = 1) " +
            "AND d.status = 'pending' AND (d.is_expired IS NULL OR d.is_expired = 0)")
    int countPendingDraftsForFamily(@Param("familyUserId") Long familyUserId);

    @Select("SELECT d.*, u.nickname as elderName, u.avatar as elderAvatar, f.nickname as familyName " +
            "FROM medicine_draft d " +
            "LEFT JOIN users u ON d.elder_user_id = u.id " +
            "LEFT JOIN users f ON d.family_user_id = f.id " +
            "WHERE d.elder_user_id IN " +
            "(SELECT friend_id FROM friendships WHERE user_id = #{familyUserId} AND status = 1) " +
            "ORDER BY d.create_time DESC")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "elderUserId", column = "elder_user_id"),
            @Result(property = "familyUserId", column = "family_user_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "operationType", column = "operation_type"),
            @Result(property = "targetMedicineId", column = "target_medicine_id"),
            @Result(property = "confidence", column = "confidence"),
            @Result(property = "medicineName", column = "medicine_name"),
            @Result(property = "medicineType", column = "medicine_type"),
            @Result(property = "spec", column = "spec"),
            @Result(property = "dosagePerTime", column = "dosage_per_time"),
            @Result(property = "doseUnit", column = "dose_unit"),
            @Result(property = "frequencyPerDay", column = "frequency_per_day"),
            @Result(property = "reminderTimes", column = "reminder_times"),
            @Result(property = "totalPackages", column = "total_packages"),
            @Result(property = "packageQuantity", column = "package_quantity"),
            @Result(property = "precautions", column = "precautions"),
            @Result(property = "manufacturer", column = "manufacturer"),
            @Result(property = "approvalNumber", column = "approval_number"),
            @Result(property = "coverImageUrl", column = "cover_image_url"),
            @Result(property = "instructionImageUrl", column = "instruction_image_url"),
            @Result(property = "voiceUrl", column = "voice_url"),
            @Result(property = "voiceText", column = "voice_text"),
            @Result(property = "rawOcrText", column = "raw_ocr_text"),
            @Result(property = "inputMethod", column = "input_method"),
            @Result(property = "barcode", column = "barcode"),
            @Result(property = "reviewTime", column = "review_time"),
            @Result(property = "reviewNote", column = "review_note"),
            @Result(property = "isExpired", column = "is_expired"),
            @Result(property = "supersededBy", column = "superseded_by"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "elderName", column = "elderName"),
            @Result(property = "elderAvatar", column = "elderAvatar"),
            @Result(property = "familyName", column = "familyName")
    })
    List<MedicineDraft> selectAllDraftsForFamily(@Param("familyUserId") Long familyUserId);

    @Insert("INSERT INTO medicine_draft (elder_user_id, status, operation_type, target_medicine_id, confidence, medicine_name, medicine_type, " +
            "spec, dosage_per_time, dose_unit, frequency_per_day, reminder_times, total_packages, " +
            "package_quantity, precautions, manufacturer, approval_number, cover_image_url, " +
            "instruction_image_url, voice_url, voice_text, raw_ocr_text, input_method, barcode, review_note, is_expired) " +
            "VALUES (#{elderUserId}, #{status}, #{operationType}, #{targetMedicineId}, #{confidence}, #{medicineName}, #{medicineType}, " +
            "#{spec}, #{dosagePerTime}, #{doseUnit}, #{frequencyPerDay}, #{reminderTimes}, #{totalPackages}, " +
            "#{packageQuantity}, #{precautions}, #{manufacturer}, #{approvalNumber}, #{coverImageUrl}, " +
            "#{instructionImageUrl}, #{voiceUrl}, #{voiceText}, #{rawOcrText}, #{inputMethod}, #{barcode}, #{reviewNote}, 0)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MedicineDraft draft);

    @Update("UPDATE medicine_draft SET status = #{status}, family_user_id = #{familyUserId}, " +
            "review_time = NOW(), review_note = #{reviewNote}, update_time = NOW() " +
            "WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") String status,
                     @Param("familyUserId") Long familyUserId, @Param("reviewNote") String reviewNote);

    @Update("UPDATE medicine_draft SET medicine_name = #{medicineName}, medicine_type = #{medicineType}, " +
            "spec = #{spec}, dosage_per_time = #{dosagePerTime}, dose_unit = #{doseUnit}, " +
            "frequency_per_day = #{frequencyPerDay}, reminder_times = #{reminderTimes}, " +
            "total_packages = #{totalPackages}, package_quantity = #{packageQuantity}, " +
            "precautions = #{precautions}, manufacturer = #{manufacturer}, approval_number = #{approvalNumber}, " +
            "update_time = NOW() WHERE id = #{id}")
    int updateMedicineInfo(MedicineDraft draft);

    @Delete("DELETE FROM medicine_draft WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT COUNT(*) FROM medicine_draft WHERE elder_user_id = #{elderUserId} AND status = 'pending' AND (is_expired IS NULL OR is_expired = 0)")
    int countPendingByElderUserId(@Param("elderUserId") Long elderUserId);

    @Update("UPDATE medicine_draft SET is_expired = 1, superseded_by = #{newDraftId}, update_time = NOW() " +
            "WHERE elder_user_id = #{elderUserId} AND target_medicine_id = #{targetMedicineId} " +
            "AND operation_type = #{operationType} AND status = 'pending' AND id != #{newDraftId} " +
            "AND (is_expired IS NULL OR is_expired = 0)")
    int markPreviousDraftsAsExpired(@Param("elderUserId") Long elderUserId,
                                    @Param("targetMedicineId") Integer targetMedicineId,
                                    @Param("operationType") String operationType,
                                    @Param("newDraftId") Long newDraftId);

    @Select("SELECT d.*, u.nickname as elderName, u.avatar as elderAvatar " +
            "FROM medicine_draft d " +
            "LEFT JOIN users u ON d.elder_user_id = u.id " +
            "WHERE d.elder_user_id = #{elderUserId} AND d.target_medicine_id = #{targetMedicineId} " +
            "AND d.operation_type = #{operationType} AND d.status = 'pending' " +
            "AND (d.is_expired IS NULL OR d.is_expired = 0) " +
            "ORDER BY d.create_time DESC LIMIT 1")
    @Results({
            @Result(property = "id", column = "id"),
            @Result(property = "elderUserId", column = "elder_user_id"),
            @Result(property = "familyUserId", column = "family_user_id"),
            @Result(property = "status", column = "status"),
            @Result(property = "operationType", column = "operation_type"),
            @Result(property = "targetMedicineId", column = "target_medicine_id"),
            @Result(property = "confidence", column = "confidence"),
            @Result(property = "medicineName", column = "medicine_name"),
            @Result(property = "medicineType", column = "medicine_type"),
            @Result(property = "spec", column = "spec"),
            @Result(property = "dosagePerTime", column = "dosage_per_time"),
            @Result(property = "doseUnit", column = "dose_unit"),
            @Result(property = "frequencyPerDay", column = "frequency_per_day"),
            @Result(property = "reminderTimes", column = "reminder_times"),
            @Result(property = "totalPackages", column = "total_packages"),
            @Result(property = "packageQuantity", column = "package_quantity"),
            @Result(property = "precautions", column = "precautions"),
            @Result(property = "manufacturer", column = "manufacturer"),
            @Result(property = "approvalNumber", column = "approval_number"),
            @Result(property = "coverImageUrl", column = "cover_image_url"),
            @Result(property = "instructionImageUrl", column = "instruction_image_url"),
            @Result(property = "voiceUrl", column = "voice_url"),
            @Result(property = "voiceText", column = "voice_text"),
            @Result(property = "rawOcrText", column = "raw_ocr_text"),
            @Result(property = "inputMethod", column = "input_method"),
            @Result(property = "barcode", column = "barcode"),
            @Result(property = "reviewTime", column = "review_time"),
            @Result(property = "reviewNote", column = "review_note"),
            @Result(property = "isExpired", column = "is_expired"),
            @Result(property = "supersededBy", column = "superseded_by"),
            @Result(property = "createTime", column = "create_time"),
            @Result(property = "updateTime", column = "update_time"),
            @Result(property = "elderName", column = "elderName"),
            @Result(property = "elderAvatar", column = "elderAvatar")
    })
    MedicineDraft selectLatestPendingDraft(@Param("elderUserId") Long elderUserId,
                                           @Param("targetMedicineId") Integer targetMedicineId,
                                           @Param("operationType") String operationType);
}

package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.MedicationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface MedicationLogMapper {
    int insert(MedicationLog log);

    int updateTaken(@Param("id") Long id, @Param("takenTime") LocalDateTime takenTime, @Param("dosageTaken") java.math.BigDecimal dosageTaken, @Param("status") String status);

    List<MedicationLog> selectByUserMedicineId(@Param("userMedicineId") Integer userMedicineId, @Param("limit") int limit);

    List<MedicationLog> selectPendingByUserId(@Param("userId") Integer userId);

    List<MedicationLog> selectTodayByUserId(@Param("userId") Integer userId);

    List<MedicationLog> selectTodayByUserMedicineId(@Param("userMedicineId") Integer userMedicineId);

    MedicationLog selectPendingByUserMedicineAndTime(@Param("userMedicineId") Integer userMedicineId, @Param("scheduledTime") LocalDateTime scheduledTime);

    MedicationLog selectTakenTodayByUserMedicineAndTime(@Param("userMedicineId") Integer userMedicineId, @Param("scheduledTime") LocalDateTime scheduledTime);

    MedicationLog selectLastTakenByUserMedicineId(@Param("userMedicineId") Integer userMedicineId);

    int deleteFuturePendingLogs(@Param("userMedicineId") Integer userMedicineId, @Param("afterTime") LocalDateTime afterTime);
}

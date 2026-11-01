package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.MedicineOperationLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MedicineOperationLogMapper {
    int insert(MedicineOperationLog log);
    
    List<MedicineOperationLog> selectByUserMedicineId(@Param("userMedicineId") Integer userMedicineId);
    
    List<MedicineOperationLog> selectByUserId(@Param("userId") Long userId);
    
    List<MedicineOperationLog> selectByOperatorId(@Param("operatorId") Long operatorId);
}

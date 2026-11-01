package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.MedicineStockAlert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface MedicineStockAlertMapper {
    int insert(MedicineStockAlert alert);
    int update(MedicineStockAlert alert);
    int markAsRead(@Param("id") Long id);
    int markAsResolved(@Param("id") Long id);
    List<MedicineStockAlert> selectUnreadByUserId(@Param("userId") Integer userId);
    List<MedicineStockAlert> selectByUserMedicineId(@Param("userMedicineId") Integer userMedicineId);
    int deleteByUserMedicineId(@Param("userMedicineId") Integer userMedicineId);
}

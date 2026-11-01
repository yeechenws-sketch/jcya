package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.UserMedicine;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserMedicineMapper {
    int insert(UserMedicine userMedicine);

    int update(UserMedicine userMedicine);

    int deleteById(@Param("id") Integer id);

    UserMedicine selectById(@Param("id") Integer id);

    UserMedicine selectByIdForUpdate(@Param("id") Integer id);

    List<UserMedicine> selectByUserId(@Param("userId") Integer userId);

    List<UserMedicine> selectLowStockByUserId(@Param("userId") Integer userId, @Param("thresholdDays") Integer thresholdDays);

    int updateStock(@Param("id") Integer id, @Param("currentStock") Integer currentStock);

    int decrementStock(@Param("id") Integer id, @Param("amount") Integer amount);

    int softDelete(@Param("id") Integer id,
                   @Param("deletedBy") Long deletedBy,
                   @Param("deletedByName") String deletedByName,
                   @Param("deletedByRole") String deletedByRole,
                   @Param("deleteReason") String deleteReason);

    int restore(@Param("id") Integer id);

    List<UserMedicine> selectDeletedByUserId(@Param("userId") Integer userId);

    UserMedicine selectByIdIncludeDeleted(@Param("id") Integer id);
}

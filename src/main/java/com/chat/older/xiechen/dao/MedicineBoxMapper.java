package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.MedicineBox;
import org.apache.ibatis.annotations.*;

@Mapper
public interface MedicineBoxMapper {
    
    @Insert("INSERT INTO medicine_box (device_id, user_id, box_name, status, battery_level, last_sync_time) " +
            "VALUES (#{deviceId}, #{userId}, #{boxName}, #{status}, #{batteryLevel}, #{lastSyncTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MedicineBox box);
    
    @Update("UPDATE medicine_box SET device_id = #{deviceId}, user_id = #{userId}, box_name = #{boxName}, " +
            "status = #{status}, battery_level = #{batteryLevel}, last_sync_time = #{lastSyncTime}, " +
            "update_time = NOW() WHERE id = #{id}")
    int update(MedicineBox box);
    
    @Select("SELECT * FROM medicine_box WHERE id = #{id}")
    MedicineBox selectById(@Param("id") Long id);
    
    @Select("SELECT * FROM medicine_box WHERE user_id = #{userId}")
    MedicineBox selectByUserId(@Param("userId") Long userId);
    
    @Select("SELECT * FROM medicine_box WHERE device_id = #{deviceId}")
    MedicineBox selectByDeviceId(@Param("deviceId") String deviceId);
    
    @Update("UPDATE medicine_box SET status = #{status}, battery_level = #{batteryLevel}, " +
            "last_sync_time = NOW(), update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status, @Param("batteryLevel") Integer batteryLevel);
}

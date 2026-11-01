package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.EmergencyContact;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface EmergencyContactMapper {
    @Select("SELECT * FROM emergency_contacts WHERE user_id = #{userId}")
    List<EmergencyContact> selectByUserId(Long userId);

    @Select("SELECT * FROM emergency_contacts WHERE user_id = #{userId} AND (review_types LIKE CONCAT('%', #{reviewType}, '%') OR review_types = 'all' OR review_types IS NULL)")
    List<EmergencyContact> selectByUserIdAndReviewType(@Param("userId") Long userId, @Param("reviewType") String reviewType);

    @Insert("INSERT INTO emergency_contacts(user_id, contact_user_id, contact_name, contact_phone, is_staff, priority, is_system_add, review_types, update_time) " +
            "VALUES(#{userId}, #{contactUserId}, #{contactName}, #{contactPhone}, #{isStaff}, #{priority}, #{isSystemAdd}, #{reviewTypes}, #{updateTime})")
    int insert(EmergencyContact emergencyContact);

    @Update("UPDATE emergency_contacts SET contact_name = #{contactName}, contact_phone = #{contactPhone}, review_types = #{reviewTypes}, update_time = #{updateTime} " +
            "WHERE user_id = #{userId} AND contact_user_id = #{contactUserId}")
    int update(EmergencyContact emergencyContact);

    @Delete("DELETE FROM emergency_contacts WHERE user_id = #{userId} AND contact_user_id = #{staffUserId}")
    int deleteSystemStaffContact(Long userId, Long staffUserId);

    @Delete("DELETE FROM emergency_contacts WHERE user_id = #{userId} AND is_staff = 0 AND is_system_add = 0")
    int deleteCustomContacts(Long userId);

    @Delete("DELETE FROM emergency_contacts WHERE id = #{id}")
    int deleteById(Long id);

    @Select("SELECT * FROM emergency_contacts WHERE id = #{id}")
    EmergencyContact selectById(Long id);

    @Select("SELECT * FROM emergency_contacts WHERE user_id = #{userId} AND contact_user_id = #{contactUserId}")
    EmergencyContact selectByUserIdAndContactUserId(Long userId, Long contactUserId);

    @Select("SELECT count(*) FROM emergency_contacts WHERE user_id = #{userId} AND contact_user_id = #{contactUserId}")
    int countRelation(Long userId, Long contactUserId);

    @Insert("INSERT INTO emergency_contacts(user_id, contact_user_id, contact_name, is_staff, is_system_add, review_types, update_time) " +
            "VALUES(#{userId}, #{contactUserId}, #{contactName}, #{isStaff}, 1, #{reviewTypes}, NOW())")
    int saveSystemContact(@Param("userId") Long userId,
                          @Param("contactUserId") Long contactUserId,
                          @Param("contactName") String contactName,
                          @Param("isStaff") Integer isStaff,
                          @Param("reviewTypes") String reviewTypes);

    @Delete("DELETE FROM emergency_contacts WHERE user_id = #{userId} AND contact_user_id = #{contactUserId} AND is_system_add = 1")
    int deleteSystemContact(@Param("userId") Long userId, @Param("contactUserId") Long contactUserId);

    @Delete("DELETE FROM emergency_contacts WHERE user_id = #{userId} AND is_system_add = 1")
    int deleteAllSystemContacts(Long userId);

    @Update("UPDATE emergency_contacts SET review_types = #{reviewTypes}, update_time = NOW() WHERE id = #{id}")
    int updateReviewTypes(@Param("id") Long id, @Param("reviewTypes") String reviewTypes);
}

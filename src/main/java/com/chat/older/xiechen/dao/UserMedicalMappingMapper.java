package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.UserMedicalMapping;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserMedicalMappingMapper {
    @Select("SELECT * FROM user_medical_mapping WHERE user_id = #{userId}")
    List<UserMedicalMapping> selectByUserId(Long userId);

    @Insert("INSERT INTO user_medical_mapping(user_id, history_id) " +
            "VALUES(#{userId}, #{historyId})")
    int insert(UserMedicalMapping userMedicalMapping);

    @Delete("DELETE FROM user_medical_mapping WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);

    @Delete("DELETE FROM user_medical_mapping WHERE user_id = #{userId} AND history_id = #{historyId}")
    int deleteByUserIdAndHistoryId(Long userId, Integer historyId);
}

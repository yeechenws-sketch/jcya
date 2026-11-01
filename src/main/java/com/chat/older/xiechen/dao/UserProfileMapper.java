package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.UserProfile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface UserProfileMapper {
    @Select("SELECT user_id, real_name, gender, age, neighborhood_id, address_detail, custom_history, user_phone FROM user_profiles WHERE user_id = #{userId}")
    UserProfile selectById(Long userId);

    @Insert("INSERT INTO user_profiles(user_id, real_name, gender, age, user_phone) " +
            "VALUES(#{userId}, #{realName}, #{gender}, #{age}, #{userPhone})")
    int insert(UserProfile profile);

    @Update("UPDATE user_profiles SET real_name = #{realName}, gender = #{gender}, age = #{age}, " +
            "neighborhood_id = #{neighborhoodId}, address_detail = #{addressDetail}, custom_history = #{customHistory}, user_phone = #{userPhone} " +
            "WHERE user_id = #{userId}")
    int update(UserProfile profile);

    @Select("SELECT COUNT(*) FROM user_profiles WHERE user_id = #{userId}")
    int countByUserId(Long userId);
}

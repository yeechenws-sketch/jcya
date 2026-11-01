package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.UserCommunity;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserCommunityMapper {
    @Select("SELECT * FROM user_community WHERE user_id = #{userId} AND status = 1")
    UserCommunity findCurrentByUserId(Long userId);

    @Insert("INSERT INTO user_community(user_id, community_id, status, move_in_time) " +
            "VALUES(#{userId}, #{communityId}, #{status}, #{moveInTime})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(UserCommunity userCommunity);

    @Update("UPDATE user_community SET status = #{status}, move_out_time = #{moveOutTime} " +
            "WHERE id = #{id}")
    int update(UserCommunity userCommunity);

    @Select("SELECT * FROM user_community WHERE id = #{id}")
    UserCommunity selectById(Long id);

    @Select("SELECT * FROM user_community WHERE user_id = #{userId} ORDER BY move_out_time DESC LIMIT 1")
    UserCommunity findLatestMoveOutByUserId(Long userId);

    @Select("SELECT * FROM user_community WHERE user_id = #{userId} ORDER BY move_in_time DESC")
    List<UserCommunity> findHistoryByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM user_community WHERE user_id = #{userId}")
    int countByUserId(Long userId);
}

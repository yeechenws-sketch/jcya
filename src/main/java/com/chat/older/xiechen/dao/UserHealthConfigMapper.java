package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.UserHealthConfig;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserHealthConfigMapper {
    int insert(UserHealthConfig record);
    int update(UserHealthConfig record);
    UserHealthConfig selectById(Long id);
    UserHealthConfig selectByUserId(Long userId);
    int upsert(UserHealthConfig record);
}

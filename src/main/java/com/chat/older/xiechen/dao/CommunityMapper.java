package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.Community;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface CommunityMapper {
    @Select("SELECT * FROM communities WHERE id = #{id}")
    Community selectById(Integer id);

    @Select("SELECT * FROM communities")
    List<Community> selectAll();
}

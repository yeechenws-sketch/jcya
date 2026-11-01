package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.Neighborhood;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface NeighborhoodMapper {
    @Select("SELECT * FROM neighborhoods WHERE id = #{id}")
    Neighborhood selectById(Integer id);

    @Select("SELECT * FROM neighborhoods WHERE community_id = #{communityId}")
    List<Neighborhood> selectByCommunityId(Integer communityId);

    @Select("SELECT * FROM neighborhoods")
    List<Neighborhood> selectAll();
}

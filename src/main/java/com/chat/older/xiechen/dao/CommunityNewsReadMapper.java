package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.CommunityNewsRead;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommunityNewsReadMapper {

    @Select("SELECT * FROM community_news_read WHERE user_id = #{userId} AND community_news_id = #{newsId}")
    CommunityNewsRead getByUserAndNews(@Param("userId") Long userId, @Param("newsId") Long newsId);

    @Select("SELECT community_news_id FROM community_news_read WHERE user_id = #{userId}")
    List<Long> getReadNewsIds(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM community_news_read WHERE user_id = #{userId} AND community_news_id IN (${newsIds})")
    int countReadNews(@Param("userId") Long userId, @Param("newsIds") String newsIds);

    @Insert("INSERT INTO community_news_read (user_id, community_news_id, read_time, read_source, read_duration) " +
            "VALUES (#{userId}, #{communityNewsId}, #{readTime}, #{readSource}, #{readDuration}) " +
            "ON DUPLICATE KEY UPDATE read_time = #{readTime}, read_source = #{readSource}, read_duration = #{readDuration}")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertOrUpdate(CommunityNewsRead record);

    @Delete("DELETE FROM community_news_read WHERE user_id = #{userId} AND community_news_id = #{newsId}")
    int deleteByUserAndNews(@Param("userId") Long userId, @Param("newsId") Long newsId);

    @Delete("DELETE FROM community_news_read WHERE community_news_id = #{newsId}")
    int deleteByNewsId(@Param("newsId") Long newsId);

    @Select("SELECT COUNT(*) FROM community_news_read WHERE community_news_id = #{newsId}")
    int countByNewsId(@Param("newsId") Long newsId);

    @Select("SELECT cnr.*, cn.title as news_title FROM community_news_read cnr " +
            "LEFT JOIN community_news cn ON cnr.community_news_id = cn.id " +
            "WHERE cnr.user_id = #{userId} ORDER BY cnr.read_time DESC LIMIT #{limit}")
    List<java.util.Map<String, Object>> getRecentReadNews(@Param("userId") Long userId, @Param("limit") int limit);
}

package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.CommunityNews;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface CommunityNewsMapper {

    @Select("SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.id = #{id}")
    CommunityNews getById(@Param("id") Long id);

    @Select("SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.community_id = #{communityId} AND cn.status = 1 " +
            "ORDER BY cn.publish_time DESC")
    List<CommunityNews> getByCommunityId(@Param("communityId") Integer communityId);

    @Select("SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.community_id = #{communityId} AND cn.status = 1 " +
            "ORDER BY cn.publish_time DESC " +
            "LIMIT #{limit}")
    List<CommunityNews> getByCommunityIdLimit(@Param("communityId") Integer communityId, @Param("limit") int limit);

    @Select("SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.community_id = #{communityId} AND cn.status = 1 " +
            "AND cn.publish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "ORDER BY cn.publish_time DESC")
    List<CommunityNews> getRecent24hByCommunityId(@Param("communityId") Integer communityId);

    @Select("<script>" +
            "SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.community_id = #{communityId} AND cn.status = 1 " +
            "<if test='category != null and category != \"\"'>" +
            "AND cn.category = #{category} " +
            "</if>" +
            "ORDER BY cn.publish_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<CommunityNews> getByCommunityIdAndCategoryPaged(
            @Param("communityId") Integer communityId,
            @Param("category") String category,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM community_news " +
            "WHERE community_id = #{communityId} AND status = 1 " +
            "AND category = #{category}")
    int countByCommunityIdAndCategory(@Param("communityId") Integer communityId, @Param("category") String category);

    @Select("SELECT COUNT(*) FROM community_news " +
            "WHERE community_id = #{communityId} AND status = 1")
    int countByCommunityId(@Param("communityId") Integer communityId);

    @Update("UPDATE community_news SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementViewCount(@Param("id") Long id);

    @Update("UPDATE community_news SET like_count = like_count + 1 WHERE id = #{id}")
    void incrementLikeCount(@Param("id") Long id);

    @Update("UPDATE community_news SET like_count = like_count - 1 WHERE id = #{id} AND like_count > 0")
    void decrementLikeCount(@Param("id") Long id);

    @Insert("INSERT INTO community_news(community_id, title, content, category, cover_image, status, publish_time, publisher_id) " +
            "VALUES(#{communityId}, #{title}, #{content}, #{category}, #{coverImage}, #{status}, #{publishTime}, #{publisherId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(CommunityNews communityNews);

    @Select("SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.community_id = #{communityId} AND cn.status = 1 " +
            "ORDER BY cn.view_count DESC " +
            "LIMIT #{limit}")
    List<CommunityNews> getHotByCommunityId(@Param("communityId") Integer communityId, @Param("limit") int limit);

    @Select("SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.community_id = #{communityId} AND cn.status = 1 " +
            "AND cn.cover_image IS NOT NULL AND cn.cover_image != '' " +
            "ORDER BY cn.publish_time DESC " +
            "LIMIT #{limit}")
    List<CommunityNews> getWithCoverByCommunityId(@Param("communityId") Integer communityId, @Param("limit") int limit);

    @Select("SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.community_id = #{communityId} AND cn.status = 1 " +
            "AND cn.cover_image IS NOT NULL AND cn.cover_image != '' " +
            "AND cn.publish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "ORDER BY cn.publish_time DESC " +
            "LIMIT #{limit}")
    List<CommunityNews> getRecent24hWithCoverByCommunityId(@Param("communityId") Integer communityId, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.community_id = #{communityId} " +
            "<if test='status != null'>" +
            "AND cn.status = #{status} " +
            "</if>" +
            "<if test='category != null and category != \"\"'>" +
            "AND cn.category = #{category} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (cn.title LIKE CONCAT('%', #{keyword}, '%') OR cn.content LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY cn.create_time DESC " +
            "LIMIT #{limit} OFFSET #{offset}" +
            "</script>")
    List<CommunityNews> getAdminListByCommunityId(
            @Param("communityId") Integer communityId,
            @Param("status") Integer status,
            @Param("category") String category,
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset);

    @Select("<script>" +
            "SELECT COUNT(*) FROM community_news " +
            "WHERE community_id = #{communityId} " +
            "<if test='status != null'>" +
            "AND status = #{status} " +
            "</if>" +
            "<if test='category != null and category != \"\"'>" +
            "AND category = #{category} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (title LIKE CONCAT('%', #{keyword}, '%') OR content LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "</script>")
    int countAdminListByCommunityId(
            @Param("communityId") Integer communityId,
            @Param("status") Integer status,
            @Param("category") String category,
            @Param("keyword") String keyword);

    @Update("UPDATE community_news SET " +
            "title = #{title}, " +
            "content = #{content}, " +
            "category = #{category}, " +
            "cover_image = #{coverImage}, " +
            "status = #{status}, " +
            "scheduled_time = #{scheduledTime}, " +
            "update_time = NOW() " +
            "WHERE id = #{id}")
    int update(CommunityNews communityNews);

    @Update("UPDATE community_news SET status = #{status}, update_time = NOW() WHERE id = #{id}")
    int updateStatus(@Param("id") Long id, @Param("status") Integer status);

    @Update("UPDATE community_news SET status = 1, publish_time = NOW(), update_time = NOW() WHERE id = #{id}")
    int publishNow(@Param("id") Long id);

    @Delete("DELETE FROM community_news WHERE id = #{id}")
    int deleteById(@Param("id") Long id);

    @Select("SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.id = #{id}")
    CommunityNews getByIdForAdmin(@Param("id") Long id);

    @Insert("INSERT INTO community_news(community_id, title, content, category, cover_image, status, publish_time, scheduled_time, publisher_id, create_time) " +
            "VALUES(#{communityId}, #{title}, #{content}, #{category}, #{coverImage}, #{status}, #{publishTime}, #{scheduledTime}, #{publisherId}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertForAdmin(CommunityNews communityNews);

    @Select("SELECT cn.*, c.community_name, u.nickname as publisher_name " +
            "FROM community_news cn " +
            "LEFT JOIN communities c ON cn.community_id = c.id " +
            "LEFT JOIN users u ON cn.publisher_id = u.id " +
            "WHERE cn.status = 2 AND cn.scheduled_time <= #{time}")
    List<CommunityNews> getScheduledNewsBeforeTime(@Param("time") java.time.LocalDateTime time);
}

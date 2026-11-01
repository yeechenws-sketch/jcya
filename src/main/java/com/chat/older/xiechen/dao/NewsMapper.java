package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.News;
import com.chat.older.xiechen.model.NewsComment;
import com.chat.older.xiechen.model.UserInterest;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NewsMapper {

    @Insert("INSERT INTO news (title, content, category, location, source, original_url, local_image_path, publish_time) " +
            "VALUES (#{title}, #{content}, #{category}, #{location}, #{source}, #{originalUrl}, #{localImagePath}, #{publishTime})")
    int insertNews(News news);

    @Select("SELECT COUNT(*) FROM news WHERE original_url = #{url}")
    int countByUrl(String url);

    @Select("SELECT * FROM news WHERE category = #{category} " +
            "AND local_image_path != '/news/default.jpg' " + // 确保猜你喜欢不显示默认图
            "ORDER BY publish_time DESC LIMIT #{limit}")
    List<News> getNewsByCategory(@Param("category") String category, @Param("limit") int limit);

    @Select("SELECT * FROM news WHERE local_image_path != '/news/default.jpg' " +
            "ORDER BY publish_time DESC LIMIT #{limit}")
    List<News> getTopNews(int limit);

    @Update("INSERT INTO user_interests (user_id, category, click_count) " +
            "VALUES (#{userId}, #{category}, 1) " +
            "ON DUPLICATE KEY UPDATE click_count = click_count + 1")
    void upsertInterest(@Param("userId") Long userId, @Param("category") String category);

    @Select("SELECT category, click_count FROM user_interests " +
            "WHERE user_id = #{userId} AND click_count > 0 " +
            "ORDER BY click_count DESC LIMIT 5")
    List<UserInterest> getTopInterests(Long userId);

    @Select("SELECT category FROM news WHERE id = #{id}")
    String getCategoryById(Long id);

    @Select("SELECT * FROM news ORDER BY publish_time DESC LIMIT #{limit}")
    List<News> getLatestNews(int limit);

    @Select("SELECT * FROM news WHERE id = #{id}")
    News getById(Long id);

    @Update("UPDATE news SET view_count = view_count + 1 WHERE id = #{id}")
    void incrementViewCount(Long id);

    @Update("UPDATE news SET like_count = like_count + 1 WHERE id = #{id}")
    void incrementNewsLikeCount(Long id);

    @Insert("INSERT INTO news_like (user_id, news_id) VALUES (#{userId}, #{newsId})")
    int insertNewsLike(@Param("userId") Long userId, @Param("newsId") Long newsId);

    @Select("SELECT n.* FROM news n INNER JOIN news_like nl ON n.id = nl.news_id WHERE nl.user_id = #{userId} ORDER BY nl.create_time DESC")
    List<News> getLikedNewsByUser(Long userId);

    @Select("SELECT c.*, u.username as userName FROM news_comment c " +
            "LEFT JOIN users u ON c.user_id = u.id " + // 将 user 改为 users
            "WHERE c.news_id = #{newsId} AND c.parent_id IS NULL " +
            "ORDER BY c.create_time DESC")
    List<NewsComment> getRootCommentsByNewsId(Long newsId);

    @Select("SELECT c.*, u.username as userName FROM news_comment c " +
            "LEFT JOIN users u ON c.user_id = u.id " + // 将 user 改为 users
            "WHERE c.parent_id = #{parentId} " +
            "ORDER BY c.create_time ASC")
    List<NewsComment> getRepliesByCommentId(Long parentId);

    @Insert("INSERT INTO news_comment(news_id, user_id, content, parent_id) " +
            "VALUES(#{newsId}, #{userId}, #{content}, #{parentId})")
    void insertComment(NewsComment comment);

    @Update("UPDATE news_comment SET like_count = like_count + 1 WHERE id = #{commentId}")
    void incrementCommentLike(Long commentId);

    @Select("SELECT * FROM news WHERE category = #{category} " +
            "AND local_image_path != '/news/default.jpg' " + // 核心过滤逻辑
            "ORDER BY publish_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<News> getNewsByCategoryPaged(@Param("category") String category, @Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT * FROM news WHERE local_image_path != '/news/default.jpg' " +
            "ORDER BY publish_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<News> getTopNewsPaged(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM news WHERE category = #{category} AND local_image_path != '/news/default.jpg'")
    int countByCategory(@Param("category") String category);

    @Select("SELECT COUNT(*) FROM news WHERE local_image_path != '/news/default.jpg'")
    int countAll();

    @Select("SELECT * FROM news " +
            "WHERE publish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "AND local_image_path != '/news/default.jpg' " +
            "ORDER BY view_count DESC " +
            "LIMIT #{limit}")
    List<News> getHotNews24h(@Param("limit") int limit);

    @Update("UPDATE news SET like_count = like_count - 1 WHERE id = #{id} AND like_count > 0")
    void decrementNewsLikeCount(Long id);

    @Delete("DELETE FROM news_like WHERE user_id = #{userId} AND news_id = #{newsId}")
    int deleteNewsLike(@Param("userId") Long userId, @Param("newsId") Long newsId);

    @Select("SELECT COUNT(*) FROM news_like WHERE user_id = #{userId} AND news_id = #{newsId}")
    int checkNewsLike(@Param("userId") Long userId, @Param("newsId") Long newsId);

    @Select("SELECT * FROM news WHERE category = #{category} " +
            "AND local_image_path != '/news/default.jpg' " +
            "AND publish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "ORDER BY RAND() LIMIT #{limit}")
    List<News> getRandomHotNewsByCategory(@Param("category") String category, @Param("limit") int limit);

    // AI总结最近/当日新闻
    @Select("<script>" +
            "SELECT * FROM news WHERE publish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "<if test='category != null and category != \"\"'> AND category = #{category} </if> " +
            "ORDER BY view_count DESC LIMIT #{limit}" +
            "</script>")
    List<News> getHotNews24hFiltered(@Param("category") String category, @Param("limit") int limit);

    @Select("<script>" +
            "SELECT * FROM news WHERE publish_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "<if test='category != null and category != \"\"'> AND category = #{category} </if> " +
            "ORDER BY publish_time DESC LIMIT #{limit}" +
            "</script>")
    List<News> getLatestNews24hFiltered(@Param("category") String category, @Param("limit") int limit);
}
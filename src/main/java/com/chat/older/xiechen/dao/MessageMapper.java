package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.Message;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface MessageMapper {

    @Insert("INSERT INTO messages (from_id, to_id, content, msg_type, duration, create_time) " +
            "VALUES (#{fromId}, #{toId}, #{content}, #{msgType}, #{duration}, NOW())")
    int insertMessage(Message msg);

    @Select("SELECT from_id as fromId, to_id as toId, content, msg_type as msgType, " +
            "duration, DATE_FORMAT(create_time, '%H:%i') as time " +
            "FROM messages " +
            "WHERE ((from_id = #{me} AND to_id = #{other} AND from_deleted = 0) " +
            "OR (from_id = #{other} AND to_id = #{me} AND to_deleted = 0)) " +
            "ORDER BY create_time ASC LIMIT 100")
    List<Map<String, Object>> getChatHistory(@Param("me") Long me, @Param("other") Long other);

    @Select("SELECT count(*) FROM messages WHERE to_id = #{myId} AND is_read = 0")
    int getTotalUnreadCount(Long myId);

    @Select("SELECT from_id, count(*) as count FROM messages WHERE to_id = #{myId} AND is_read = 0 GROUP BY from_id")
    List<Map<String, Object>> getUnreadDetail(Long myId);

    @Update("UPDATE messages SET is_read = 1 WHERE to_id = #{myId} AND from_id = #{fromId} AND is_read = 0")
    void markAsRead(@Param("myId") Long myId, @Param("fromId") Long fromId);

    @Update("UPDATE messages SET from_deleted = 1 WHERE from_id = #{me} AND to_id = #{other}")
    void markFromDeleted(@Param("me") Long me, @Param("other") Long other);

    @Update("UPDATE messages SET to_deleted = 1 WHERE to_id = #{me} AND from_id = #{other}")
    void markToDeleted(@Param("me") Long me, @Param("other") Long other);

    @Delete("DELETE FROM messages WHERE from_deleted = 1 AND to_deleted = 1")
    void physicalDelete();

    @Select("SELECT id, from_id as fromId, to_id as toId, content, msg_type as msgType, duration, create_time as createTime " +
            "FROM messages " +
            "WHERE ((from_id = #{userId} AND to_id = #{adminId}) " +
            "OR (from_id = #{adminId} AND to_id = #{userId}))")
    List<Map<String, Object>> getChatHistoryForArchive(@Param("userId") Long userId, @Param("adminId") Long adminId);

    @Update("UPDATE messages SET from_deleted = 1 WHERE from_id = #{userId} AND to_id = #{adminId}")
    void markFromDeletedForArchive(@Param("userId") Long userId, @Param("adminId") Long adminId);

    @Update("UPDATE messages SET to_deleted = 1 WHERE to_id = #{userId} AND from_id = #{adminId}")
    void markToDeletedForArchive(@Param("userId") Long userId, @Param("adminId") Long adminId);

    @Select("SELECT id, from_id as fromId, to_id as toId, content, msg_type as msgType, duration, create_time as createTime " +
            "FROM messages " +
            "WHERE (from_id = #{userId} OR to_id = #{userId})")
    List<Map<String, Object>> getAllChatHistoryForArchive(@Param("userId") Long userId);

    @Update("UPDATE messages SET from_deleted = 1 WHERE from_id = #{userId}")
    void markAllFromDeletedForArchive(@Param("userId") Long userId);

    @Update("UPDATE messages SET to_deleted = 1 WHERE to_id = #{userId}")
    void markAllToDeletedForArchive(@Param("userId") Long userId);

    @Update("UPDATE messages SET is_read = 1 WHERE (from_id = #{userId} OR to_id = #{userId}) AND is_read = 0")
    void markAllAsReadByUserId(@Param("userId") Long userId);

    @Delete("DELETE FROM messages WHERE from_id = #{userId} OR to_id = #{userId}")
    void physicalDeleteAllByUserId(@Param("userId") Long userId);

    @Select("SELECT id, from_id as fromId, to_id as toId, content, msg_type as msgType, duration, create_time as createTime " +
            "FROM messages " +
            "WHERE ((from_id = #{userId} AND to_id = #{adminId}) " +
            "OR (from_id = #{adminId} AND to_id = #{userId})) " +
            "AND (is_archived = 0 OR is_archived IS NULL)")
    List<Map<String, Object>> getUnarchivedChatHistory(@Param("userId") Long userId, @Param("adminId") Long adminId);

    @Update("UPDATE messages SET is_archived = 1 " +
            "WHERE ((from_id = #{userId} AND to_id = #{adminId}) " +
            "OR (from_id = #{adminId} AND to_id = #{userId})) " +
            "AND (is_archived = 0 OR is_archived IS NULL)")
    int markAsArchived(@Param("userId") Long userId, @Param("adminId") Long adminId);
}
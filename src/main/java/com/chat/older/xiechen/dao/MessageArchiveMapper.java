package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.MessageArchive;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Options;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface MessageArchiveMapper {
    @Insert("INSERT INTO message_archive (original_msg_id, from_id, to_id, content, msg_type, duration, create_time, archive_time, archived_by, reason, community_id) " +
            "VALUES (#{originalMsgId}, #{fromId}, #{toId}, #{content}, #{msgType}, #{duration}, #{createTime}, NOW(), #{archivedBy}, #{reason}, #{communityId})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(MessageArchive archive);

    @Select("SELECT id, original_msg_id as originalMsgId, from_id as fromId, to_id as toId, " +
            "content, msg_type as msgType, duration, create_time as createTime, " +
            "archive_time as archiveTime, archived_by as archivedBy, reason, community_id as communityId " +
            "FROM message_archive " +
            "WHERE archived_by = #{userId} " +
            "ORDER BY create_time ASC")
    List<Map<String, Object>> getArchiveMessagesByUserId(Long userId);

    @Select("SELECT id, original_msg_id as originalMsgId, from_id as fromId, to_id as toId, " +
            "content, msg_type as msgType, duration, create_time as createTime, " +
            "archive_time as archiveTime, archived_by as archivedBy, reason, community_id as communityId " +
            "FROM message_archive " +
            "WHERE archived_by = #{userId} " +
            "AND ((from_id = #{userId} AND to_id = #{otherId}) OR (from_id = #{otherId} AND to_id = #{userId})) " +
            "ORDER BY create_time ASC")
    List<Map<String, Object>> getArchiveMessagesByUserIdAndOtherId(Long userId, Long otherId);

    @Select("SELECT DISTINCT u.id, u.nickname, u.avatar " +
            "FROM message_archive ma " +
            "JOIN users u ON (u.id = ma.from_id OR u.id = ma.to_id) AND u.id != ma.archived_by " +
            "WHERE ma.archived_by = #{userId}")
    List<Map<String, Object>> getArchiveContactsByUserId(Long userId);
}

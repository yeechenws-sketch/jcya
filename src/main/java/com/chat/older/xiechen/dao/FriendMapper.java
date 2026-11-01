package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.Friendship;
import com.chat.older.xiechen.vo.FriendRequestVO;
import com.chat.older.xiechen.vo.UserVO;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface FriendMapper {
    @Insert("INSERT INTO friendships (user_id, friend_id, remark, status) VALUES (#{userId}, #{friendId}, #{remark}, 2)")
    int saveRequest(Long userId, Long friendId, String remark);

    @Update("UPDATE friendships SET status = #{status} WHERE id = #{id}")
    int updateStatus(Long id, Integer status);

    @Update("UPDATE friendships SET remark = #{remark} WHERE user_id = #{userId} AND friend_id = #{friendId}")
    int updateRemark(Long userId, Long friendId, String remark);

    @Update("UPDATE friendships SET remark = #{remark}, relationship = #{relationship} WHERE user_id = #{userId} AND friend_id = #{friendId}")
    int updateRemarkAndRelationship(@Param("userId") Long userId, @Param("friendId") Long friendId, @Param("remark") String remark, @Param("relationship") String relationship);


    @Select("SELECT count(*) FROM friendships WHERE (user_id = #{userId} AND friend_id = #{friendId}) OR (user_id = #{friendId} AND friend_id = #{userId})")
    int countRelation(Long userId, Long friendId);

    @Delete("DELETE FROM friendships WHERE (user_id = #{myId} AND friend_id = #{friendId}) " +
            "OR (user_id = #{friendId} AND friend_id = #{myId})")
    int deleteFriend(Long myId, Long friendId);

    @Select("SELECT * FROM friendships WHERE id = #{id}")
    Friendship getById(Long id);

    @Insert("INSERT INTO friendships (user_id, friend_id, remark, status) " +
            "VALUES (#{userId}, #{friendId}, #{remark}, #{status})")
    int saveFriend(@Param("userId") Long userId,
                   @Param("friendId") Long friendId,
                   @Param("remark") String remark,
                   @Param("status") Integer status);

    @Select("SELECT f.id, u.nickname as fromNickname, u.avatar as fromAvatar, f.user_id as fromId " +
            "FROM friendships f JOIN users u ON f.user_id = u.id " +
            "WHERE f.friend_id = #{myId} AND f.status = 2")
    List<FriendRequestVO> getPendingRequests(Long myId);

    @Select("SELECT u.id, u.nickname, u.avatar, f.remark " +
            "FROM friendships f JOIN users u ON f.friend_id = u.id " +
            "WHERE f.user_id = #{myId} AND f.status = 1")
    List<UserVO> getMyFriends(Long myId);

    @Select("SELECT u.id, u.nickname, u.avatar, u.role, f.remark, f.relationship, " +
            "m.content as lastMessage, m.msg_type as lastMsgType, m.create_time as lastMsgTime " +
            "FROM friendships f " +
            "JOIN users u ON f.friend_id = u.id " +
            "LEFT JOIN messages m ON ((m.from_id = #{myId} AND m.to_id = u.id) OR (m.from_id = u.id AND m.to_id = #{myId})) " +
            "AND m.id = (SELECT MAX(id) FROM messages WHERE (from_id = #{myId} AND to_id = u.id) OR (from_id = u.id AND to_id = #{myId})) " +
            "WHERE f.user_id = #{myId} AND f.status = 1 " +
            "ORDER BY COALESCE(m.create_time, '1970-01-01') DESC")
    List<Map<String, Object>> getMyFriendsOrderByLastMessage(Long myId);

    @Delete("DELETE FROM friendships WHERE user_id = #{userId}")
    int deleteAllByUserId(Long userId);

    @Select("SELECT * FROM friendships WHERE user_id = #{userId} AND friend_id = #{friendId} AND status = 1")
    Friendship getAcceptedFriendship(Long userId, Long friendId);

    @Select("SELECT * FROM friendships WHERE user_id = #{userId} AND friend_id = #{friendId}")
    Friendship getFriendship(Long userId, Long friendId);

    @Update("UPDATE friendships SET status = 0 WHERE user_id = #{userId} AND friend_id = #{friendId}")
    int updateStatusToDeleted(Long userId, Long friendId);

    @Select("SELECT f.id, f.user_id as userId, f.friend_id as friendId, f.remark, f.status, f.create_time as createTime, " +
            "u.username as friendUsername, u.nickname as friendNickname, u.avatar as friendAvatar " +
            "FROM friendships f " +
            "JOIN users u ON f.friend_id = u.id " +
            "WHERE f.user_id = #{userId} AND f.status = 1 " +
            "ORDER BY f.create_time DESC")
    List<Friendship> getFriendshipsByUserId(Long userId);
}

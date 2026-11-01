package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.User;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface UserMapper {
    @Select("SELECT count(*) FROM users WHERE username = #{username}")
    int countByUsername(String username);

    @Insert("INSERT INTO users(username, password, nickname, avatar, role) " +
            "VALUES(#{username}, #{password}, #{nickname}, #{avatar}, #{role})"
    )
    int insertUser(User user);

    @Select("SELECT * FROM users WHERE id = #{id}")
    User getById(Long id);

    @Select("SELECT * FROM users WHERE username = #{username}")
    User getByUsername(String Username);

    @Select("SELECT * FROM users WHERE username = #{username} AND password = #{password}")
    User login(String username, String password);

    @Select("SELECT id, nickname, avatar FROM users WHERE id != #{myId}")
    List<User> getAllContacts(Long myId);

    @Select("SELECT u.id, f.remark as nickname, u.avatar " +
            "FROM friendships f " +
            "JOIN users u ON f.friend_id = u.id " +
            "WHERE f.user_id = #{myId} AND f.status = 1"
    )
    List<User> getMyFriends(Long myId);

    @Select("SELECT * FROM users WHERE username = #{username}")
    User findByUsername(String username);

    @Update("UPDATE users SET nickname = #{nickname}, avatar = #{avatar} WHERE id = #{id}")
    int update(User user);

    @Update("UPDATE users SET password = #{password} WHERE id = #{id}")
    int updatePassword(User user);

    @Update("UPDATE users SET avatar = #{avatar} WHERE id = #{id}")
    int updateAvatar(@Param("id") Long id, @Param("avatar") String avatar);

    @Select("SELECT * FROM users")
    List<User> selectAll();
}



package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.User;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface UserManageMapper {
    
    @Select("SELECT id, username, password, nickname, avatar, role FROM users")
    List<User> findAll();
    
    @Select("SELECT id, username, password, nickname, avatar, role FROM users WHERE id = #{id}")
    User findById(Long id);
    
    @Select("SELECT id, username, password, nickname, avatar, role FROM users WHERE username = #{username}")
    User findByUsername(String username);
    
    @Insert("INSERT INTO users (username, password, nickname, avatar, role) " +
            "VALUES (#{username}, #{password}, #{nickname}, #{avatar}, #{role})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(User user);
    
    @Update("UPDATE users SET username = #{username}, nickname = #{nickname}, " +
            "avatar = #{avatar}, role = #{role} WHERE id = #{id}")
    int update(User user);
    
    @Update("UPDATE users SET password = #{password} WHERE id = #{id}")
    int updatePassword(@Param("id") Long id, @Param("password") String password);
    
    @Delete("DELETE FROM users WHERE id = #{id}")
    int deleteById(Long id);
    
    @Select("SELECT COUNT(*) FROM users")
    int countAll();
    
    @Select("SELECT COUNT(*) FROM users WHERE role = #{role}")
    int countByRole(String role);
}

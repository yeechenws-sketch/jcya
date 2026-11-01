package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.ChatRecord;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface ChatRecordMapper {
    @Insert("INSERT INTO chat_records(user_id, role, content) VALUES(#{userId}, #{role}, #{content})")
    int insertRecord(ChatRecord record);


    @Delete("DELETE FROM chat_records WHERE user_id = #{userId}")
    int deleteByUserId(Long userId);

    @Select("SELECT role, content FROM chat_records WHERE user_id = #{userId} ORDER BY create_time ASC")
    List<ChatRecord> findByUserId(Long userId);
}

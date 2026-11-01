package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.CallLog;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface CallLogMapper {

    @Insert("INSERT INTO call_logs (from_id, to_id, type, duration, start_time, end_time, direction, status) " +
            "VALUES (#{fromId}, #{toId}, #{type}, #{duration}, #{startTime}, #{endTime}, #{direction}, #{status})")
    int insert(CallLog callLog);

    @Select("SELECT * FROM call_logs WHERE from_id = #{userId} OR to_id = #{userId} ORDER BY end_time DESC LIMIT 50")
    List<CallLog> findByUserId(Long userId);

    @Select("SELECT COUNT(*) FROM call_logs WHERE to_id = #{userId} AND direction = 'incoming' AND status = 0")
    int countMissedCalls(Long userId);

    @Update("UPDATE call_logs SET status = 3 WHERE to_id = #{userId} AND direction = 'incoming' AND duration = 0 AND status = 0")
    int markMissedCallsAsRead(Long userId);

    @Select("SELECT * FROM call_logs WHERE (from_id = #{userId} OR to_id = #{userId}) " +
            "AND (status != 3 OR (duration > 0 AND status = 1)) " +
            "ORDER BY end_time DESC LIMIT 50")
    List<CallLog> findForFrontend(Long userId);
}
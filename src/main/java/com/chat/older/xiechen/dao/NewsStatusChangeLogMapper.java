package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.NewsStatusChangeLog;
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface NewsStatusChangeLogMapper {

    @Insert("INSERT INTO news_status_change_log " +
            "(news_id, news_title, old_status, new_status, operator_id, operator_name, cleared_read_count, operate_time, operate_ip, remark) " +
            "VALUES (#{newsId}, #{newsTitle}, #{oldStatus}, #{newStatus}, #{operatorId}, #{operatorName}, #{clearedReadCount}, #{operateTime}, #{operateIp}, #{remark})")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(NewsStatusChangeLog log);

    @Select("SELECT * FROM news_status_change_log WHERE news_id = #{newsId} ORDER BY operate_time DESC")
    List<NewsStatusChangeLog> getByNewsId(@Param("newsId") Long newsId);

    @Select("SELECT * FROM news_status_change_log ORDER BY operate_time DESC LIMIT #{limit} OFFSET #{offset}")
    List<NewsStatusChangeLog> getList(@Param("limit") int limit, @Param("offset") int offset);

    @Select("SELECT COUNT(*) FROM news_status_change_log")
    int count();
}

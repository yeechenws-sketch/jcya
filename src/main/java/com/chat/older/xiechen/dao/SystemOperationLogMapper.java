package com.chat.older.xiechen.dao;

import com.chat.older.xiechen.model.SystemOperationLog;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface SystemOperationLogMapper {
    
    @Insert("INSERT INTO system_operation_log (log_type, operation, detail, operator_id, operator_name, operator_ip, create_time) " +
            "VALUES (#{logType}, #{operation}, #{detail}, #{operatorId}, #{operatorName}, #{operatorIp}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insert(SystemOperationLog log);
    
    @Select("<script>" +
            "SELECT * FROM system_operation_log WHERE 1=1 " +
            "<if test='logType != null and logType != \"\"'>" +
            "AND log_type = #{logType} " +
            "</if>" +
            "<if test='startDate != null'>" +
            "AND DATE(create_time) &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null'>" +
            "AND DATE(create_time) &lt;= #{endDate} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (operation LIKE CONCAT('%', #{keyword}, '%') OR detail LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "ORDER BY create_time DESC " +
            "LIMIT #{offset}, #{pageSize}" +
            "</script>")
    List<SystemOperationLog> selectByPage(@Param("logType") String logType,
                                          @Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate,
                                          @Param("keyword") String keyword,
                                          @Param("offset") int offset,
                                          @Param("pageSize") int pageSize);
    
    @Select("<script>" +
            "SELECT COUNT(*) FROM system_operation_log WHERE 1=1 " +
            "<if test='logType != null and logType != \"\"'>" +
            "AND log_type = #{logType} " +
            "</if>" +
            "<if test='startDate != null'>" +
            "AND DATE(create_time) &gt;= #{startDate} " +
            "</if>" +
            "<if test='endDate != null'>" +
            "AND DATE(create_time) &lt;= #{endDate} " +
            "</if>" +
            "<if test='keyword != null and keyword != \"\"'>" +
            "AND (operation LIKE CONCAT('%', #{keyword}, '%') OR detail LIKE CONCAT('%', #{keyword}, '%')) " +
            "</if>" +
            "</script>")
    int countByCondition(@Param("logType") String logType,
                         @Param("startDate") LocalDateTime startDate,
                         @Param("endDate") LocalDateTime endDate,
                         @Param("keyword") String keyword);
    
    @Select("SELECT DISTINCT log_type FROM system_operation_log ORDER BY log_type")
    List<String> selectAllLogTypes();
    
    @Delete("DELETE FROM system_operation_log")
    int deleteAll();
}

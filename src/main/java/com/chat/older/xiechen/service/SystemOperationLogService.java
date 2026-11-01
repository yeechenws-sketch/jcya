package com.chat.older.xiechen.service;

import com.chat.older.xiechen.dao.SystemOperationLogMapper;
import com.chat.older.xiechen.model.SystemOperationLog;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class SystemOperationLogService {

    @Autowired
    private SystemOperationLogMapper logMapper;

    public void addLog(String logType, String operation, String detail, Long operatorId, String operatorName, String operatorIp) {
        SystemOperationLog log = new SystemOperationLog();
        log.setLogType(logType);
        log.setOperation(operation);
        log.setDetail(detail);
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setOperatorIp(operatorIp);
        logMapper.insert(log);
    }

    public Map<String, Object> getLogsByPage(String logType, LocalDate startDate, LocalDate endDate, 
                                              String keyword, int page, int pageSize) {
        int offset = (page - 1) * pageSize;
        
        LocalDateTime startDateTime = startDate != null ? startDate.atStartOfDay() : null;
        LocalDateTime endDateTime = endDate != null ? endDate.atTime(23, 59, 59) : null;
        
        List<SystemOperationLog> logs = logMapper.selectByPage(logType, startDateTime, endDateTime, keyword, offset, pageSize);
        int total = logMapper.countByCondition(logType, startDateTime, endDateTime, keyword);
        
        Map<String, Object> result = new HashMap<>();
        result.put("list", logs);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        result.put("pages", (int) Math.ceil((double) total / pageSize));
        
        return result;
    }

    public List<String> getAllLogTypes() {
        return logMapper.selectAllLogTypes();
    }

    public void clearAllLogs() {
        logMapper.deleteAll();
    }
}

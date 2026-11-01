package com.chat.older.xiechen.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Date;

@Data
public class EmergencyCallRecord {
    private Long id;
    private Long elderUserId;
    private String elderUserName;
    private String callType;
    private String alertType;
    private Integer alertLevel;
    private String alertValue;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long answeredByUserId;
    private String answeredByUserName;
    private String status;
    private String notificationStatus;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

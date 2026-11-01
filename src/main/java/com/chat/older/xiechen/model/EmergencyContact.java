package com.chat.older.xiechen.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class EmergencyContact {
    private Long id;
    private Long userId;
    private Long contactUserId;
    private String contactName;
    private String contactPhone;
    private Integer isStaff;
    private Integer priority;
    private Integer isSystemAdd;
    private String reviewTypes;
    private LocalDateTime updateTime;
    
    public static final String REVIEW_TYPE_MEDICINE = "medicine";
    public static final String REVIEW_TYPE_HEALTH = "health";
    public static final String REVIEW_TYPE_EMERGENCY = "emergency";
    public static final String REVIEW_TYPE_ALL = "all";
}

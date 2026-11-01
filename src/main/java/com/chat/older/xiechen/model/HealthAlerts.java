package com.chat.older.xiechen.model;

import lombok.Data;
import java.util.Date;

@Data
public class HealthAlerts {
    private Long id;
    private Long userId;
    private String alertType;
    private Integer alertLevel;
    private Float currentValue;
    private Float thresholdValue;
    private Long vitalSignId;
    private Integer isRead;
    private Date readTime;
    private Date createTime;
}

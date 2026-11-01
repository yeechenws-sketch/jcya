package com.chat.older.xiechen.model;

import lombok.Data;
import java.util.Date;

@Data
public class HealthAdviceRecord {
    private Long id;
    private Long userId;
    private String userName;
    private Long adminId;
    private String adminName;
    private String adviceType;
    private String adviceContent;
    private String suggestedValues;
    private Integer status;
    private Date createTime;
}

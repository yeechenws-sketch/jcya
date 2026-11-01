package com.chat.older.xiechen.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class HealthAdvice {
    private Long id;
    private String adviceType;
    private String title;
    private String content;
    private Integer priority;
    private Integer isActive;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

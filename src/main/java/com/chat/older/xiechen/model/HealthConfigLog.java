package com.chat.older.xiechen.model;

import lombok.Data;
import java.util.Date;

@Data
public class HealthConfigLog {
    private Long id;
    private Long userId;
    private String userName;
    private Long operatorId;
    private String operatorName;
    private String operatorType;
    private String configField;
    private String oldValue;
    private String newValue;
    private String changeDescription;
    private Date createTime;
}

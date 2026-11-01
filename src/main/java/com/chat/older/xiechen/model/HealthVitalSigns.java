package com.chat.older.xiechen.model;

import lombok.Data;
import java.util.Date;

@Data
public class HealthVitalSigns {
    private Long id;
    private Long userId;
    private String type;
    private Float value1;
    private Float value2;
    private Integer status;
    private String deviceId;
    private String remark;
    private Date createTime;
    private Date updateTime;
}

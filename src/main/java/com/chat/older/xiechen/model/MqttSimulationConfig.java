package com.chat.older.xiechen.model;

import lombok.Data;
import java.util.Date;

@Data
public class MqttSimulationConfig {
    private Long id;
    private Long userId;
    private String configName;
    private String dataType;
    private Float value1;
    private Float value2;
    private String deviceId;
    private Integer autoSend;
    private Integer sendInterval;
    private Integer isEnabled;
    private Date createTime;
    private Date updateTime;
}

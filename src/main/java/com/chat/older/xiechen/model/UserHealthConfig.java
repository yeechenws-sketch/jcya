package com.chat.older.xiechen.model;

import lombok.Data;
import java.util.Date;

@Data
public class UserHealthConfig {
    private Long id;
    private Long userId;

    private Integer hrMin;
    private Integer hrMax;

    private Integer boMin;
    private Integer boWarning;

    private Integer bpSysMin;
    private Integer bpSysMax;
    private Integer bpSysEmergency;
    private Integer bpDiaMin;
    private Integer bpDiaMax;
    private Integer bpDiaEmergency;

    private Float bgFastingMin;
    private Float bgFastingMax;
    private Float bgAfterMealMin;
    private Float bgAfterMealMax;

    private Integer stepTarget;
    private Float sleepTarget;

    private Integer enableNotifications;
    private String notificationTime;
    
    private Date updateTime;
}

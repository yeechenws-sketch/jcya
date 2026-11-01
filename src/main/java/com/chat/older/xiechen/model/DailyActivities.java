package com.chat.older.xiechen.model;

import lombok.Data;
import java.util.Date;

@Data
public class DailyActivities {
    private Long id;
    private Long userId;
    private Date recordDate;
    private Integer steps;
    private Float sleepHours;
    private Integer calories;
    private Float distance;
    private Date updateTime;
}

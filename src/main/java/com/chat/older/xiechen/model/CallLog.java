package com.chat.older.xiechen.model;

import lombok.Data;
import java.util.Date;

@Data
public class CallLog {
    private Long id;
    private Long fromId;
    private Long toId;
    private String type;
    private Integer duration;
    private Date startTime;
    private Date endTime;
    private String direction;
    private Integer status;
}
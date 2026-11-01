package com.chat.older.xiechen.model;

import lombok.Data;
import java.util.Date;
@Data
public class Message {
    private Long id;
    private Long fromId;
    private Long toId;
    private String content;
    private String msgType;
    private Integer isRead;
    private Date createTime;
    private Integer duration;
    private Integer isArchived;
}

package com.chat.older.xiechen.model;

import lombok.Data;

@Data
public class AutoMessageTemplate {
    private Integer id;
    private String scene;
    private String content;
    private Integer isEnabled;
    private String remark;
    private java.time.LocalDateTime createTime;
    private java.time.LocalDateTime updateTime;
}

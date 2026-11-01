package com.chat.older.xiechen.model;

import lombok.Data;

@Data
public class RingtoneConfig {
    private Long id;
    private String type;
    private String name;
    private String url;
    private Boolean enabled;
    private String updateTime;
}

package com.chat.older.xiechen.model;

import lombok.Data;

@Data
public class Community {
    private Integer id;
    private String communityName;
    private Long adminUserId;
    private String adminPhone;
}

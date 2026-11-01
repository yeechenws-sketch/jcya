package com.chat.older.xiechen.model;

import lombok.Data;

@Data
public class UserProfile {
    private Long userId;
    private String realName;
    private String gender;
    private Integer age;
    private Integer communityId;
    private Integer neighborhoodId;
    private String addressDetail;
    private String customHistory;
    private String userPhone;
}

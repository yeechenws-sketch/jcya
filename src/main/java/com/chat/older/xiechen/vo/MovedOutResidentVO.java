package com.chat.older.xiechen.vo;

import lombok.Data;

@Data
public class MovedOutResidentVO {
    private Long userId;
    private String realName;
    private String gender;
    private Integer age;
    private String moveInTime;
    private String moveOutTime;
    private Long recordId;
}

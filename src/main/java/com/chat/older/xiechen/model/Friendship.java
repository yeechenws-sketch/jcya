package com.chat.older.xiechen.model;

import lombok.Data;
import java.util.Date;

@Data
public class Friendship {
    private Long id;
    private Long userId;
    private Long friendId;
    private String remark;
    private String relationship;
    private Integer status;
    private Date createTime;
    private String friendUsername;
    private String friendNickname;
    private String friendAvatar;
}
package com.chat.older.xiechen.vo;

import lombok.Data;

@Data
public class UserVO {
    private Long id;           // 用户ID
    private String username;   // 登录账号
    private String nickname;   // 原始昵称
    private String avatar;     // 头像URL
    private String remark;     // 核心：好友关系中的备注名
    private Integer unreadCount; // 可选：用于前端显示未读消息数
}
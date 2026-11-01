package com.chat.older.xiechen.vo;

import lombok.Data;

@Data
public class FriendRequestVO {
    private Long id;           // 申请记录的ID (friendships表主键)
    private Long fromId;       // 申请人的用户ID
    private String fromNickname; // 申请人的昵称
    private String fromAvatar;   // 申请人的头像
}

package com.chat.older.xiechen.model;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class NewsComment {
    private Long id;
    private Long newsId;
    private Long userId;
    private String content;
    private Long parentId;
    private Integer likeCount;
    private LocalDateTime createTime;

    //前端展示
    private String userName;
    private String userAvatar;
    private List<NewsComment> replies;
}
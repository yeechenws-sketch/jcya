package com.chat.older.xiechen.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class News {
    private Long id;
    private String title;
    private String content;
    private String category;
    private String location;
    private String source;
    private String originalUrl;
    private String localImagePath;
    private Integer viewCount;
    private Integer likeCount;
    private LocalDateTime publishTime;
    private LocalDateTime createTime;
}
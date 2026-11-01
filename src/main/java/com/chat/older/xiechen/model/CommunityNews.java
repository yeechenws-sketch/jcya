package com.chat.older.xiechen.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class CommunityNews {
    private Long id;
    private Integer communityId;
    private String title;
    private String content;
    private String category;
    private String coverImage;
    private Integer viewCount;
    private Integer likeCount;
    private Integer status;
    private LocalDateTime publishTime;
    private LocalDateTime scheduledTime;
    private Long publisherId;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    
    private String communityName;
    private String publisherName;
}

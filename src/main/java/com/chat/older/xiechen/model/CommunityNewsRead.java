package com.chat.older.xiechen.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("community_news_read")
public class CommunityNewsRead {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long communityNewsId;

    private LocalDateTime readTime;

    private String readSource;

    private Integer readDuration;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}

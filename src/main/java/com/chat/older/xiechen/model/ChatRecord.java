package com.chat.older.xiechen.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatRecord {
    private Long id;
    private Long userId;
    private String role;
    private String content;
    private LocalDateTime createTime;
}



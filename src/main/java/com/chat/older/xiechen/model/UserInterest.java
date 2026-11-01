package com.chat.older.xiechen.model;
import lombok.Data;

@Data
public class UserInterest {
    private Long id;
    private Long userId;
    private String category;
    private Integer clickCount;
}
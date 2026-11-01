package com.chat.older.xiechen.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class UserCommunity {
    private Long id;
    private Long userId;
    private Integer communityId;
    private Integer status;
    private LocalDateTime moveInTime;
    private LocalDateTime moveOutTime;
}

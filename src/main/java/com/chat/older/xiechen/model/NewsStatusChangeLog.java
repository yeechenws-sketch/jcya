package com.chat.older.xiechen.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class NewsStatusChangeLog {
    private Long id;
    private Long newsId;
    private String newsTitle;
    private Integer oldStatus;
    private Integer newStatus;
    private Long operatorId;
    private String operatorName;
    private Integer clearedReadCount;
    private LocalDateTime operateTime;
    private String operateIp;
    private String remark;
}

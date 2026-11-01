package com.chat.older.xiechen.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class MessageArchive {
    private Long id;
    private Long originalMsgId;
    private Long fromId;
    private Long toId;
    private String content;
    private String msgType;
    private Integer duration;
    private LocalDateTime createTime;
    private LocalDateTime archiveTime;
    private Long archivedBy;
    private String reason;
    private Integer communityId;
}

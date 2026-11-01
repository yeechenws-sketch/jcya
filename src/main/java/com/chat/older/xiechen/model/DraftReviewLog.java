package com.chat.older.xiechen.model;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class DraftReviewLog {
    private Long id;
    private Long draftId;
    private Long reviewerId;
    private String reviewerName;
    private Long elderId;
    private String elderName;
    private String action;
    private String originalData;
    private String modifiedData;
    private String reviewNote;
    private LocalDateTime createTime;
    
    public static final String ACTION_APPROVE = "approve";
    public static final String ACTION_APPROVE_WITH_EDIT = "approve_with_edit";
    public static final String ACTION_REJECT = "reject";
    public static final String ACTION_DELETE = "delete";
}

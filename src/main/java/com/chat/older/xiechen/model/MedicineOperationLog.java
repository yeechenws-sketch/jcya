package com.chat.older.xiechen.model;

import java.time.LocalDateTime;

public class MedicineOperationLog {
    private Long id;
    private Integer userMedicineId;
    private Long userId;
    private Long operatorId;
    private String operatorName;
    private String operatorRole;
    private String operationType;
    private String beforeData;
    private String afterData;
    private String remark;
    private LocalDateTime createTime;

    public static final String TYPE_CREATE = "create";
    public static final String TYPE_UPDATE = "update";
    public static final String TYPE_DELETE = "delete";
    public static final String TYPE_RESTORE = "restore";

    public static final String ROLE_CHILD = "子女";
    public static final String ROLE_ELDER = "老人";
    public static final String ROLE_COMMUNITY = "社区";

    public MedicineOperationLog() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getUserMedicineId() {
        return userMedicineId;
    }

    public void setUserMedicineId(Integer userMedicineId) {
        this.userMedicineId = userMedicineId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getOperatorId() {
        return operatorId;
    }

    public void setOperatorId(Long operatorId) {
        this.operatorId = operatorId;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getOperatorRole() {
        return operatorRole;
    }

    public void setOperatorRole(String operatorRole) {
        this.operatorRole = operatorRole;
    }

    public String getOperationType() {
        return operationType;
    }

    public void setOperationType(String operationType) {
        this.operationType = operationType;
    }

    public String getBeforeData() {
        return beforeData;
    }

    public void setBeforeData(String beforeData) {
        this.beforeData = beforeData;
    }

    public String getAfterData() {
        return afterData;
    }

    public void setAfterData(String afterData) {
        this.afterData = afterData;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }
}

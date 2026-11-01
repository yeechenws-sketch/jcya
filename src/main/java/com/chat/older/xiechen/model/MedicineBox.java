package com.chat.older.xiechen.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MedicineBox {
    private Long id;
    private String deviceId;
    private Long userId;
    private String boxName;
    private Integer status;
    private Integer batteryLevel;
    private LocalDateTime lastSyncTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Boolean isLocked;
    private LocalDateTime lockedUntil;
    private Boolean initialized;
    private BigDecimal singleDoseWeight;
    private BigDecimal fullBottleWeight;
    private BigDecimal toleranceMin;
    private BigDecimal toleranceMax;
    private BigDecimal secondWeighingWeight;
    private String initStep;
    private BigDecimal initFirstWeight;
    private Integer pillsPerBottle;

    public MedicineBox() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getDeviceId() { return deviceId; }
    public void setDeviceId(String deviceId) { this.deviceId = deviceId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getBoxName() { return boxName; }
    public void setBoxName(String boxName) { this.boxName = boxName; }
    public Integer getStatus() { return status; }
    public void setStatus(Integer status) { this.status = status; }
    public Integer getBatteryLevel() { return batteryLevel; }
    public void setBatteryLevel(Integer batteryLevel) { this.batteryLevel = batteryLevel; }
    public LocalDateTime getLastSyncTime() { return lastSyncTime; }
    public void setLastSyncTime(LocalDateTime lastSyncTime) { this.lastSyncTime = lastSyncTime; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
    public Boolean getIsLocked() { return isLocked; }
    public void setIsLocked(Boolean isLocked) { this.isLocked = isLocked; }
    public LocalDateTime getLockedUntil() { return lockedUntil; }
    public void setLockedUntil(LocalDateTime lockedUntil) { this.lockedUntil = lockedUntil; }
    public Boolean getInitialized() { return initialized; }
    public void setInitialized(Boolean initialized) { this.initialized = initialized; }
    public BigDecimal getSingleDoseWeight() { return singleDoseWeight; }
    public void setSingleDoseWeight(BigDecimal singleDoseWeight) { this.singleDoseWeight = singleDoseWeight; }
    public BigDecimal getFullBottleWeight() { return fullBottleWeight; }
    public void setFullBottleWeight(BigDecimal fullBottleWeight) { this.fullBottleWeight = fullBottleWeight; }
    public BigDecimal getToleranceMin() { return toleranceMin; }
    public void setToleranceMin(BigDecimal toleranceMin) { this.toleranceMin = toleranceMin; }
    public BigDecimal getToleranceMax() { return toleranceMax; }
    public void setToleranceMax(BigDecimal toleranceMax) { this.toleranceMax = toleranceMax; }
    public BigDecimal getSecondWeighingWeight() { return secondWeighingWeight; }
    public void setSecondWeighingWeight(BigDecimal secondWeighingWeight) { this.secondWeighingWeight = secondWeighingWeight; }
    public String getInitStep() { return initStep; }
    public void setInitStep(String initStep) { this.initStep = initStep; }
    public BigDecimal getInitFirstWeight() { return initFirstWeight; }
    public void setInitFirstWeight(BigDecimal initFirstWeight) { this.initFirstWeight = initFirstWeight; }
    public Integer getPillsPerBottle() { return pillsPerBottle; }
    public void setPillsPerBottle(Integer pillsPerBottle) { this.pillsPerBottle = pillsPerBottle; }
}

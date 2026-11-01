package com.chat.older.xiechen.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class MedicationLog {
    private Long id;
    private Integer userMedicineId;
    private LocalDateTime scheduledTime;
    private LocalDateTime takenTime;
    private BigDecimal dosageTaken;
    private String status;
    private String source;
    private LocalDateTime createTime;
    
    private String medicineName;
    private String doseUnit;
    private String reminderTimes;

    public MedicationLog() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Integer getUserMedicineId() { return userMedicineId; }
    public void setUserMedicineId(Integer userMedicineId) { this.userMedicineId = userMedicineId; }
    public LocalDateTime getScheduledTime() { return scheduledTime; }
    public void setScheduledTime(LocalDateTime scheduledTime) { this.scheduledTime = scheduledTime; }
    public LocalDateTime getTakenTime() { return takenTime; }
    public void setTakenTime(LocalDateTime takenTime) { this.takenTime = takenTime; }
    public BigDecimal getDosageTaken() { return dosageTaken; }
    public void setDosageTaken(BigDecimal dosageTaken) { this.dosageTaken = dosageTaken; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public String getMedicineName() { return medicineName; }
    public void setMedicineName(String medicineName) { this.medicineName = medicineName; }
    public String getDoseUnit() { return doseUnit; }
    public void setDoseUnit(String doseUnit) { this.doseUnit = doseUnit; }
    public String getReminderTimes() { return reminderTimes; }
    public void setReminderTimes(String reminderTimes) { this.reminderTimes = reminderTimes; }
}

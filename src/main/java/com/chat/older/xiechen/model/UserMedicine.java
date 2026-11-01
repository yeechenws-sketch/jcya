package com.chat.older.xiechen.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class UserMedicine {
    private Integer id;
    private Integer userId;
    private Integer medicineId;
    private String customName;
    private String type;
    private String spec;
    private String packageUnit;
    private Integer packageQuantity;
    private String doseUnit;
    private BigDecimal dosagePerTime;
    private Integer frequencyPerDay;
    private String reminderTimes;
    private Integer totalPackages;
    private Integer totalStock;
    private Integer currentStock;
    private Integer thresholdDays;
    private LocalDate startDate;
    private LocalDate endDate;
    private Integer status;
    private Integer isDeleted;
    private LocalDateTime deletedTime;
    private Long deletedBy;
    private String deletedByName;
    private String deletedByRole;
    private String deleteReason;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private String medicineName;
    private String barcode;
    private String drugInstruction;
    private String indication;
    private String precautions;

    private transient List<String> reminderTimesList;

    private Integer daysLeft;
    private Double stockPercent;
    private String nextReminder;

    public UserMedicine() {
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public Integer getUserId() {
        return userId;
    }

    public void setUserId(Integer userId) {
        this.userId = userId;
    }

    public Integer getMedicineId() {
        return medicineId;
    }

    public void setMedicineId(Integer medicineId) {
        this.medicineId = medicineId;
    }

    public String getCustomName() {
        return customName;
    }

    public void setCustomName(String customName) {
        this.customName = customName;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getSpec() {
        return spec;
    }

    public void setSpec(String spec) {
        this.spec = spec;
    }

    public String getPackageUnit() {
        return packageUnit;
    }

    public void setPackageUnit(String packageUnit) {
        this.packageUnit = packageUnit;
    }

    public Integer getPackageQuantity() {
        return packageQuantity;
    }

    public void setPackageQuantity(Integer packageQuantity) {
        this.packageQuantity = packageQuantity;
    }

    public String getDoseUnit() {
        return doseUnit;
    }

    public void setDoseUnit(String doseUnit) {
        this.doseUnit = doseUnit;
    }

    public BigDecimal getDosagePerTime() {
        return dosagePerTime;
    }

    public void setDosagePerTime(BigDecimal dosagePerTime) {
        this.dosagePerTime = dosagePerTime;
    }

    public Integer getFrequencyPerDay() {
        return frequencyPerDay;
    }

    public void setFrequencyPerDay(Integer frequencyPerDay) {
        this.frequencyPerDay = frequencyPerDay;
    }

    public String getReminderTimes() {
        return reminderTimes;
    }

    public void setReminderTimes(String reminderTimes) {
        this.reminderTimes = reminderTimes;
    }

    public Integer getTotalPackages() {
        return totalPackages;
    }

    public void setTotalPackages(Integer totalPackages) {
        this.totalPackages = totalPackages;
    }

    public Integer getTotalStock() {
        return totalStock;
    }

    public void setTotalStock(Integer totalStock) {
        this.totalStock = totalStock;
    }

    public Integer getCurrentStock() {
        return currentStock;
    }

    public void setCurrentStock(Integer currentStock) {
        this.currentStock = currentStock;
    }

    public Integer getThresholdDays() {
        return thresholdDays;
    }

    public void setThresholdDays(Integer thresholdDays) {
        this.thresholdDays = thresholdDays;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getIsDeleted() {
        return isDeleted;
    }

    public void setIsDeleted(Integer isDeleted) {
        this.isDeleted = isDeleted;
    }

    public LocalDateTime getDeletedTime() {
        return deletedTime;
    }

    public void setDeletedTime(LocalDateTime deletedTime) {
        this.deletedTime = deletedTime;
    }

    public Long getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(Long deletedBy) {
        this.deletedBy = deletedBy;
    }

    public String getDeletedByName() {
        return deletedByName;
    }

    public void setDeletedByName(String deletedByName) {
        this.deletedByName = deletedByName;
    }

    public String getDeletedByRole() {
        return deletedByRole;
    }

    public void setDeletedByRole(String deletedByRole) {
        this.deletedByRole = deletedByRole;
    }

    public String getDeleteReason() {
        return deleteReason;
    }

    public void setDeleteReason(String deleteReason) {
        this.deleteReason = deleteReason;
    }

    public LocalDateTime getCreateTime() {
        return createTime;
    }

    public void setCreateTime(LocalDateTime createTime) {
        this.createTime = createTime;
    }

    public LocalDateTime getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(LocalDateTime updateTime) {
        this.updateTime = updateTime;
    }

    public String getMedicineName() {
        return medicineName;
    }

    public void setMedicineName(String medicineName) {
        this.medicineName = medicineName;
    }

    public String getBarcode() {
        return barcode;
    }

    public void setBarcode(String barcode) {
        this.barcode = barcode;
    }

    public String getDrugInstruction() {
        return drugInstruction;
    }

    public void setDrugInstruction(String drugInstruction) {
        this.drugInstruction = drugInstruction;
    }

    public String getIndication() {
        return indication;
    }

    public void setIndication(String indication) {
        this.indication = indication;
    }

    public String getPrecautions() {
        return precautions;
    }

    public void setPrecautions(String precautions) {
        this.precautions = precautions;
    }

    public List<String> getReminderTimesList() {
        return reminderTimesList;
    }

    public void setReminderTimesList(List<String> reminderTimesList) {
        this.reminderTimesList = reminderTimesList;
    }

    public Integer getDaysLeft() {
        return daysLeft;
    }

    public void setDaysLeft(Integer daysLeft) {
        this.daysLeft = daysLeft;
    }

    public Double getStockPercent() {
        return stockPercent;
    }

    public void setStockPercent(Double stockPercent) {
        this.stockPercent = stockPercent;
    }

    public String getNextReminder() {
        return nextReminder;
    }

    public void setNextReminder(String nextReminder) {
        this.nextReminder = nextReminder;
    }
}

package com.chat.older.xiechen.model;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class MedicineDraft {
    private Long id;
    private Long elderUserId;
    private Long familyUserId;

    private String status;
    private String operationType;
    private Integer targetMedicineId;
    private BigDecimal confidence;

    private String medicineName;
    private String medicineType;
    private String spec;
    private BigDecimal dosagePerTime;
    private String doseUnit;
    private Integer frequencyPerDay;
    private String reminderTimes;
    private Integer totalPackages;
    private Integer packageQuantity;
    private String precautions;
    private String manufacturer;
    private String approvalNumber;

    private String coverImageUrl;
    private String instructionImageUrl;
    private String voiceUrl;
    private String voiceText;
    private String rawOcrText;

    private String inputMethod;
    private String barcode;

    private LocalDateTime reviewTime;
    private String reviewNote;

    private Boolean isExpired;
    private Long supersededBy;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private transient List<String> reminderTimesList;

    private String elderName;
    private String elderAvatar;
    private String familyName;

    public static final String STATUS_PENDING = "pending";
    public static final String STATUS_APPROVED = "approved";
    public static final String STATUS_REJECTED = "rejected";
    public static final String STATUS_DELETED = "deleted";

    public static final String OP_CREATE = "create";
    public static final String OP_UPDATE = "update";
    public static final String OP_DELETE = "delete";

    public static final String INPUT_MANUAL = "manual";
    public static final String INPUT_OCR = "ocr";
    public static final String INPUT_VOICE = "voice";
    public static final String INPUT_BARCODE = "barcode";
}

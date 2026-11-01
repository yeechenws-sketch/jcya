package com.chat.older.xiechen.vo;

import lombok.Data;
import java.util.List;

@Data
public class ResidentVO {
    private Long userId;
    private String realName;
    private String gender;
    private Integer age;
    private String phone;
    private Integer neighborhoodId;
    private String neighborhoodName;
    private String addressDetail;
    private List<String> historyList;
    private String customHistory;
    private String moveInTime;
    private HealthStatusVO healthStatus;
}

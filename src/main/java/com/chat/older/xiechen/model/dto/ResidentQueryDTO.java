package com.chat.older.xiechen.model.dto;

import lombok.Data;
import java.util.List;

@Data
public class ResidentQueryDTO {
    private Integer neighborhoodId;
    private String gender;
    private List<Integer> historyIds;
    private String sortType;
    private Integer residentStatus;
    private String keyword;
    private Integer page;
    private Integer pageSize;
}

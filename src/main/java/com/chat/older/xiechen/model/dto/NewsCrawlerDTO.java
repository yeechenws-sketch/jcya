package com.chat.older.xiechen.model.dto;

import com.chat.older.xiechen.model.News;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
public class NewsCrawlerDTO extends News {
    private String searchKeyword;
}
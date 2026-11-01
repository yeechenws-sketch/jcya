package com.chat.older.xiechen.service;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.data.image.Image; // 必须导入这个类

public interface MedicineExtractor {
    @UserMessage("请识别图片中的信息。")
    String extract(Image image);
}
package com.chat.older.xiechen.service;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

import static dev.langchain4j.service.spring.AiServiceWiringMode.EXPLICIT;

@AiService(wiringMode = EXPLICIT, chatModel = "textChatModel")
public interface NewsSummarizer {

    @SystemMessage("你是一个为老年人服务的贴心新闻助手小智。请将输入的新闻内容总结为100-200字的摘要。" +
            "要求：\n" +
            "1. 语言通俗易懂，语气亲切温暖，像在和长辈聊天；\n" +
            "2. 去除无关的广告、免责声明或乱码；\n" +
            "3. 只保留最核心的新闻事实。")
    String summarize(@UserMessage String content);
}
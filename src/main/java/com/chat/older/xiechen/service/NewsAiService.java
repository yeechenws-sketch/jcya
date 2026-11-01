//package com.chat.older.xiechen.service;
////总结当日新闻，按照用户兴趣，类似于闹钟/语音助手的每日新闻播报
//import dev.langchain4j.service.SystemMessage;
//import dev.langchain4j.service.UserMessage;
//import dev.langchain4j.service.spring.AiService;
//
//@AiService
//public interface NewsAiService {
//
//    @SystemMessage({
//            "你是一个专业的新闻播报员，擅长将复杂的新闻提炼为通俗易懂的简报。",
//            "请根据用户提供的多条新闻标题，总结出一份 200 字以内的每日简报。",
//            "要求：语气亲切、重点突出、逻辑清晰，适合老年人阅读。"
//    })
//    String summarizeNews(@UserMessage String newsContent);
//}
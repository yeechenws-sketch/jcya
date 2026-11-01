package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.ChatRecordMapper;
import com.chat.older.xiechen.dao.CommunityNewsMapper;
import com.chat.older.xiechen.dao.NewsMapper;
import com.chat.older.xiechen.model.ChatRecord;
import com.chat.older.xiechen.model.CommunityNews;
import com.chat.older.xiechen.model.News;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.service.AiAssistant;
import com.chat.older.xiechen.service.NewsSummarizer;
import com.chat.older.xiechen.service.HealthDataIntegratedAiService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@CrossOrigin
public class AiController {

    @Resource
    private AiAssistant aiAssistant;

    @Resource
    private HealthDataIntegratedAiService healthDataIntegratedAiService;

    @Resource
    private ChatRecordMapper chatRecordMapper;

    @Resource
    private NewsSummarizer newsSummarizer;

    @Resource
    private NewsMapper newsMapper;

    @Resource
    private CommunityNewsMapper communityNewsMapper;

    @GetMapping("/ask")
    public String ask(String msg, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return "还没登录呢，请先登录再和我聊天哦";

        Long userId = user.getId(); // 已经获取到了用户ID


        ChatRecord userRec = new ChatRecord(null, userId, "user", msg, LocalDateTime.now());
        chatRecordMapper.insertRecord(userRec);

        try {
            String aiReply = healthDataIntegratedAiService.chatWithHealthData(userId, msg);

            ChatRecord aiRec = new ChatRecord(null, userId, "ai", aiReply, LocalDateTime.now());
            chatRecordMapper.insertRecord(aiRec);

            return aiReply;
        } catch (Exception e) {
            e.printStackTrace();
            return "哎呀，我刚才走神了（网络连接失败），请您再说一遍。";
        }
    }


    @GetMapping("/history")
    public List<ChatRecord> getHistory(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return new ArrayList<>();
        }
        return chatRecordMapper.findByUserId(user.getId());
    }

    @PostMapping("/clearHistory")
    public String clearHistory(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user != null) {
            chatRecordMapper.deleteByUserId(user.getId());
            return "已清空";
        }
        return "未登录";
    }

    @PostMapping("/api/chat/send")
    public Map<String, Object> sendChatMessage(@RequestBody Map<String, Object> request, HttpSession session) {
        Map<String, Object> response = new HashMap<>();
        
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            response.put("code", 401);
            response.put("message", "未登录");
            return response;
        }

        Long userId = user.getId();
        String message = (String) request.get("message");

        if (message == null || message.trim().isEmpty()) {
            response.put("code", 400);
            response.put("message", "消息内容不能为空");
            return response;
        }

        try {
            ChatRecord userRec = new ChatRecord(null, userId, "user", message, LocalDateTime.now());
            chatRecordMapper.insertRecord(userRec);
            String aiReply = healthDataIntegratedAiService.chatWithHealthData(userId, message);
            ChatRecord aiRec = new ChatRecord(null, userId, "ai", aiReply, LocalDateTime.now());
            chatRecordMapper.insertRecord(aiRec);
            response.put("code", 200);
            response.put("data", aiReply);
            response.put("message", "success");
            
        } catch (Exception e) {
            e.printStackTrace();
            response.put("code", 500);
            response.put("message", "AI回答失败: " + e.getMessage());
        }

        return response;
    }

    @GetMapping("/news/summary/{id}")
    public String summarizeNews(@PathVariable Long id, 
                                @RequestParam(required = false, defaultValue = "platform") String type) {
        String content = null;
        String title = null;
        
        if ("community".equals(type)) {
            CommunityNews communityNews = communityNewsMapper.getById(id);
            if (communityNews == null || communityNews.getContent() == null) {
                return "哎呀，这篇社区新闻好像找不到内容了，没法帮您总结呢。";
            }
            content = communityNews.getContent();
            title = communityNews.getTitle();
        } else {
            News news = newsMapper.getById(id);
            if (news == null || news.getContent() == null) {
                return "哎呀，这篇新闻好像找不到内容了，没法帮您总结呢。";
            }
            content = news.getContent();
            title = news.getTitle();
        }
        
        content = content.replaceAll("<[^>]*>", "");
        if (content.length() > 3000) {
            content = content.substring(0, 3000);
        }
        return newsSummarizer.summarize(content);
    }



}
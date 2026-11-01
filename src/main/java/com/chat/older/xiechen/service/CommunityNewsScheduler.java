package com.chat.older.xiechen.service;

import com.chat.older.xiechen.dao.CommunityNewsMapper;
import com.chat.older.xiechen.model.CommunityNews;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class CommunityNewsScheduler {

    @Autowired
    private CommunityNewsMapper communityNewsMapper;

    @Scheduled(cron = "0 */1 * * * ?")
    public void checkScheduledNews() {
        LocalDateTime now = LocalDateTime.now();
        
        List<CommunityNews> scheduledList = communityNewsMapper.getScheduledNewsBeforeTime(now);
        
        for (CommunityNews news : scheduledList) {
            try {
                communityNewsMapper.publishNow(news.getId());
                System.out.println("✅ 定时新闻已发布: [ID=" + news.getId() + "] " + news.getTitle());
            } catch (Exception e) {
                System.err.println("❌ 定时新闻发布失败: [ID=" + news.getId() + "] " + e.getMessage());
            }
        }
    }
}

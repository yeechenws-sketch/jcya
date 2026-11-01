package com.chat.older.xiechen.service;

import com.chat.older.xiechen.dao.NewsMapper;
import com.chat.older.xiechen.model.News;
import com.chat.older.xiechen.model.UserInterest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
public class NewsRecommendService {

    @Autowired
    private NewsMapper newsMapper;

//个性化推荐算法：x/(x1+x2+...+xn) * 5

    public List<News> getPersonalizedRecommend(Long userId, Long currentNewsId) {
        List<News> finalRecommendations = new ArrayList<>();
        Set<Long> excludedIds = new HashSet<>();

        if (currentNewsId != null) {
            excludedIds.add(currentNewsId);
        }

        String currentCategory = null;
        if (currentNewsId != null) {
            currentCategory = newsMapper.getCategoryById(currentNewsId);
            if (currentCategory != null) {
                List<News> relativeNews = newsMapper.getRandomHotNewsByCategory(currentCategory, 1);
                finalRecommendations.addAll(relativeNews);
                relativeNews.forEach(n -> excludedIds.add(n.getId()));
            }
        }

        List<UserInterest> interests = newsMapper.getTopInterests(userId);

        for (UserInterest interest : interests) {
            if (finalRecommendations.size() >= 5) break;

            if (interest.getCategory().equals(currentCategory)) continue;

            List<News> historyMatch = newsMapper.getRandomHotNewsByCategory(interest.getCategory(), 1);
            for (News n : historyMatch) {
                if (!excludedIds.contains(n.getId()) && finalRecommendations.size() < 5) {
                    finalRecommendations.add(n);
                    excludedIds.add(n.getId());
                }
            }
        }

        if (finalRecommendations.size() < 5) {
            List<News> hotNews = newsMapper.getHotNews24h(10);
            for (News n : hotNews) {
                if (finalRecommendations.size() >= 5) break;
                if (!excludedIds.contains(n.getId())) {
                    finalRecommendations.add(n);
                    excludedIds.add(n.getId());
                }
            }
        }

        return finalRecommendations;
    }

    public void recordClick(Long userId, Long newsId) {
        String category = newsMapper.getCategoryById(newsId);
        if (category != null) {
            newsMapper.upsertInterest(userId, category);
        }
    }

    @Transactional
    public boolean handleNewsLike(Long userId, Long newsId) {
        int result = newsMapper.insertNewsLike(userId, newsId);
        if (result > 0) {
            newsMapper.incrementNewsLikeCount(newsId);
            recordClick(userId, newsId);
            return true;
        }

        return false;
    }
}
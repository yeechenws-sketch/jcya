package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.dao.NewsMapper;
import com.chat.older.xiechen.dao.CommunityNewsMapper;
import com.chat.older.xiechen.dao.CommunityNewsReadMapper;
import com.chat.older.xiechen.dao.UserCommunityMapper;
import com.chat.older.xiechen.model.*;
import com.chat.older.xiechen.service.*;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/news")
@CrossOrigin
public class NewsController {

    @Autowired
    private NewsMapper newsMapper;
    @Autowired
    private CommunityNewsMapper communityNewsMapper;
    @Autowired
    private CommunityNewsReadMapper communityNewsReadMapper;
    @Autowired
    private UserCommunityMapper userCommunityMapper;
    @Autowired
    private NewsCrawlerService newsCrawlerService;
    @Autowired
    private NewsRecommendService recommendService;
    @Autowired
    private NewsCommentService commentService;
    @Autowired
    private NewsSummarizer newsSummarizer;

    @GetMapping("/carousel")
    public List<News> getCarousel() {
        return newsMapper.getTopNews(5);
    }

    @GetMapping("/forceCrawl")
    public String forceCrawl() {
        return newsCrawlerService.triggerManualCrawl();
    }
    
    @GetMapping("/crawler/status")
    public Map<String, Object> getCrawlerStatus() {
        return newsCrawlerService.getCrawlerStatus();
    }

    @PostMapping("/click")
    public void clickNews(HttpSession session, @RequestParam Long newsId) {
        User user = (User) session.getAttribute("currUser");
        if (user != null) {
            recommendService.recordClick(user.getId(), newsId);
        }
    }

    @GetMapping("/personal-recommend")
    public List<News> getPersonal(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return newsMapper.getTopNews(5);
        }
        return recommendService.getPersonalizedRecommend(user.getId(), null);
    }

    @GetMapping("/list")
    public Map<String, Object> getNewsList(
            @RequestParam String category,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {

        int offset = (page - 1) * limit;
        List<News> list;
        int totalCount;

        if ("推荐".equals(category)) {
            list = newsMapper.getTopNewsPaged(limit, offset);
            totalCount = newsMapper.countAll();
        } else {
            list = newsMapper.getNewsByCategoryPaged(category, limit, offset);
            totalCount = newsMapper.countByCategory(category);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", list);
        result.put("total", totalCount);
        result.put("currentPage", page);
        result.put("totalPages", (int) Math.ceil((double) totalCount / limit));

        return result;
    }

    @GetMapping("/detail/{id}")
    public News getDetail(HttpSession session, @PathVariable Long id) {
        newsMapper.incrementViewCount(id);
        User user = (User) session.getAttribute("currUser");
        if (user != null) {
            recommendService.recordClick(user.getId(), id);
        }

        return newsMapper.getById(id);
    }

    @PostMapping("/like")
    public String likeNews(HttpSession session, @RequestParam Long newsId) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return "请先登录";

        Long userId = user.getId();
        int count = newsMapper.checkNewsLike(userId, newsId);

        if (count > 0) {
            newsMapper.deleteNewsLike(userId, newsId);
            newsMapper.decrementNewsLikeCount(newsId);
            return "取消点赞成功";
        } else {
            newsMapper.insertNewsLike(userId, newsId);
            newsMapper.incrementNewsLikeCount(newsId);
            return "点赞成功";
        }
    }

    @GetMapping("/guess-like")
    public List<News> getGuessLike(HttpSession session, @RequestParam(required = false) Long currentNewsId) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) {
            return recommendService.getPersonalizedRecommend(null, currentNewsId);
        }
        return recommendService.getPersonalizedRecommend(user.getId(), currentNewsId);
    }

    @GetMapping("/comments/{id}")
    public List<com.chat.older.xiechen.model.NewsComment> getComments(@PathVariable Long id) {
        return commentService.getCommentsByNewsId(id);
    }
    @PostMapping("/comment/add")
    public String addComment(HttpSession session, @RequestBody com.chat.older.xiechen.model.NewsComment comment) {
        com.chat.older.xiechen.model.User user = (com.chat.older.xiechen.model.User) session.getAttribute("currUser");
        if (user == null) {
            return "请先登录";
        }
        comment.setUserId(user.getId());
        commentService.postComment(comment);
        return "提交成功";
    }
    @GetMapping("/today-hot")
    public List<News> getTodayHot(@RequestParam(defaultValue = "8") int limit) {
        return newsMapper.getHotNews24h(limit);
    }
    @GetMapping("/daily-summary")
    public Map<String, Object> getDailySummary(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("currUser");
        Integer communityId = getCurrentUserCommunityId(session);

        StringBuilder contentToSummarize = new StringBuilder();
        List<Long> communityNewsIds = new ArrayList<>();
        if (communityId != null) {
            List<CommunityNews> communityNews = communityNewsMapper.getRecent24hWithCoverByCommunityId(communityId, 4);
            if (communityNews != null && !communityNews.isEmpty()) {
                contentToSummarize.append("【社区新闻】\n");
                for (CommunityNews cn : communityNews) {
                    contentToSummarize.append("• ").append(cn.getTitle()).append("\n");
                    communityNewsIds.add(cn.getId());
                }
                contentToSummarize.append("\n");
            }
        }
        String category = null;
        List<News> hot6 = newsMapper.getHotNews24hFiltered(category, 6);
        if (hot6 != null && !hot6.isEmpty()) {
            contentToSummarize.append("【热点新闻】\n");
            for (News n : hot6) {
                contentToSummarize.append("• ").append(n.getTitle()).append("\n");
            }
            contentToSummarize.append("\n");
        }
        List<News> latest4 = newsMapper.getLatestNews24hFiltered(category, 4);
        if (latest4 != null && !latest4.isEmpty()) {
            contentToSummarize.append("【最新新闻】\n");
            for (News n : latest4) {
                contentToSummarize.append("• ").append(n.getTitle()).append("\n");
            }
        }
        if (contentToSummarize.length() == 0) {
            result.put("summary", "今日暂无足够的新闻资讯生成简报。");
            result.put("communityNewsIds", communityNewsIds);
            return result;
        }
        result.put("summary", newsSummarizer.summarize(contentToSummarize.toString()));
        result.put("communityNewsIds", communityNewsIds);
        
        return result;
    }
    private Integer getCurrentUserCommunityId(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        System.out.println("获取社区ID - 当前用户: " + (user != null ? user.getId() : "未登录"));
        if (user == null) {
            return null;
        }
        UserCommunity userCommunity = userCommunityMapper.findCurrentByUserId(user.getId());
        System.out.println("用户社区关联: " + (userCommunity != null ? "communityId=" + userCommunity.getCommunityId() + ", status=" + userCommunity.getStatus() : "无"));
        if (userCommunity == null || userCommunity.getStatus() != 1) {
            return null;
        }
        return userCommunity.getCommunityId();
    }

    @GetMapping("/community/list")
    public Map<String, Object> getCommunityNewsList(
            HttpSession session,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit) {
        
        Integer communityId = getCurrentUserCommunityId(session);
        Map<String, Object> result = new HashMap<>();
        
        System.out.println("获取社区新闻列表 - communityId: " + communityId);
        
        if (communityId == null) {
            result.put("list", new ArrayList<>());
            result.put("total", 0);
            result.put("currentPage", page);
            result.put("totalPages", 0);
            result.put("hasCommunity", false);
            result.put("message", "您尚未加入任何社区");
            result.put("unreadCount", 0);
            System.out.println("返回结果: hasCommunity=false");
            return result;
        }

        int offset = (page - 1) * limit;
        List<CommunityNews> list = communityNewsMapper.getByCommunityIdAndCategoryPaged(communityId, null, limit, offset);
        int totalCount = communityNewsMapper.countByCommunityId(communityId);
        
        System.out.println("查询结果 - 新闻数量: " + list.size() + ", 总数: " + totalCount);

        User user = (User) session.getAttribute("currUser");
        int unreadCount = 0;
        List<Long> readIds = new ArrayList<>();
        
        if (user != null) {
            readIds = communityNewsReadMapper.getReadNewsIds(user.getId());
            List<CommunityNews> allNews = communityNewsMapper.getByCommunityId(communityId);
            for (CommunityNews news : allNews) {
                if (!readIds.contains(news.getId())) {
                    unreadCount++;
                }
            }
        } else {
            unreadCount = totalCount;
        }

        result.put("list", list);
        result.put("total", totalCount);
        result.put("currentPage", page);
        result.put("totalPages", (int) Math.ceil((double) totalCount / limit));
        result.put("hasCommunity", true);
        result.put("communityId", communityId);
        result.put("unreadCount", unreadCount);
        result.put("readIds", readIds);
        
        return result;
    }

    @GetMapping("/community/detail/{id}")
    public Map<String, Object> getCommunityNewsDetail(HttpSession session, @PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        
        Integer userCommunityId = getCurrentUserCommunityId(session);
        if (userCommunityId == null) {
            result.put("success", false);
            result.put("message", "您尚未加入任何社区，无法查看社区新闻");
            return result;
        }

        CommunityNews news = communityNewsMapper.getById(id);
        if (news == null) {
            result.put("success", false);
            result.put("message", "新闻不存在");
            return result;
        }

        if (!news.getCommunityId().equals(userCommunityId)) {
            result.put("success", false);
            result.put("message", "您无权查看该社区的新闻");
            return result;
        }

        communityNewsMapper.incrementViewCount(id);
        result.put("success", true);
        result.put("data", news);
        return result;
    }


    @GetMapping("/community/carousel")
    public List<Map<String, Object>> getCommunityCarousel(HttpSession session) {
        List<Map<String, Object>> result = new ArrayList<>();
        Integer communityId = getCurrentUserCommunityId(session);

        int communityNewsCount = 0;
        if (communityId != null) {
            List<CommunityNews> communityNews = communityNewsMapper.getRecent24hWithCoverByCommunityId(communityId, 4);
            for (CommunityNews cn : communityNews) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", cn.getId());
                item.put("title", cn.getTitle());
                item.put("coverImage", cn.getCoverImage());
                item.put("type", "community");
                item.put("communityName", cn.getCommunityName());
                result.add(item);
                communityNewsCount++;
            }
        }

        if (communityNewsCount < 5) {
            List<News> platformNews = newsMapper.getTopNews(5 - communityNewsCount);
            for (News n : platformNews) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", n.getId());
                item.put("title", n.getTitle());
                item.put("coverImage", n.getLocalImagePath());
                item.put("type", "platform");
                result.add(item);
            }
        }

        return result;
    }


    @GetMapping("/community/hot")
    public List<CommunityNews> getCommunityHotNews(HttpSession session, @RequestParam(defaultValue = "5") int limit) {
        Integer communityId = getCurrentUserCommunityId(session);
        if (communityId == null) {
            return new ArrayList<>();
        }
        return communityNewsMapper.getHotByCommunityId(communityId, limit);
    }

    @GetMapping("/mixed-recommend")
    public Map<String, Object> getMixedRecommend(
            HttpSession session,
            @RequestParam(defaultValue = "10") int limit) {
        
        Map<String, Object> result = new HashMap<>();
        List<Object> mixedList = new ArrayList<>();
        Integer communityId = getCurrentUserCommunityId(session);

        int communityNewsCount = 0;
        if (communityId != null) {
            List<CommunityNews> communityNews = communityNewsMapper.getRecent24hByCommunityId(communityId);
            for (CommunityNews cn : communityNews) {
                if (mixedList.size() >= limit) break;
                Map<String, Object> item = new HashMap<>();
                item.put("id", cn.getId());
                item.put("title", cn.getTitle());
                item.put("category", cn.getCategory());
                item.put("coverImage", cn.getCoverImage());
                item.put("viewCount", cn.getViewCount());
                item.put("publishTime", cn.getPublishTime());
                item.put("type", "community");
                item.put("communityName", cn.getCommunityName());
                mixedList.add(item);
                communityNewsCount++;
            }
        }

        if (mixedList.size() < limit) {
            User user = (User) session.getAttribute("currUser");
            List<News> platformNews;
            if (user != null) {
                platformNews = recommendService.getPersonalizedRecommend(user.getId(), null);
            } else {
                platformNews = newsMapper.getTopNews(limit - mixedList.size());
            }
            
            for (News n : platformNews) {
                if (mixedList.size() >= limit) break;
                Map<String, Object> item = new HashMap<>();
                item.put("id", n.getId());
                item.put("title", n.getTitle());
                item.put("category", n.getCategory());
                item.put("coverImage", n.getLocalImagePath());
                item.put("viewCount", n.getViewCount());
                item.put("publishTime", n.getPublishTime());
                item.put("type", "platform");
                item.put("source", n.getSource());
                mixedList.add(item);
            }
        }

        result.put("list", mixedList);
        result.put("communityNewsCount", communityNewsCount);
        result.put("hasCommunity", communityId != null);
        
        return result;
    }

    @GetMapping("/community/info")
    public Map<String, Object> getCommunityInfo(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Integer communityId = getCurrentUserCommunityId(session);
        
        if (communityId == null) {
            result.put("hasCommunity", false);
            result.put("message", "您尚未加入任何社区");
            return result;
        }

        result.put("hasCommunity", true);
        result.put("communityId", communityId);
        
        return result;
    }

    @GetMapping("/community/unread-count")
    public Map<String, Object> getUnreadCount(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        Integer communityId = getCurrentUserCommunityId(session);
        User user = (User) session.getAttribute("currUser");
        
        System.out.println("获取未读数量 - communityId: " + communityId + ", user: " + (user != null ? user.getId() : "null"));
        
        if (communityId == null) {
            result.put("unreadCount", 0);
            result.put("hasCommunity", false);
            return result;
        }
        
        if (user == null) {
            int totalCount = communityNewsMapper.countByCommunityId(communityId);
            result.put("unreadCount", totalCount);
            result.put("hasCommunity", true);
            return result;
        }
        
        List<Long> readIds = communityNewsReadMapper.getReadNewsIds(user.getId());
        List<CommunityNews> allNews = communityNewsMapper.getByCommunityId(communityId);
        
        System.out.println("已读新闻ID列表: " + readIds);
        System.out.println("所有新闻数量: " + allNews.size());
        
        int unreadCount = 0;
        for (CommunityNews news : allNews) {
            if (!readIds.contains(news.getId())) {
                unreadCount++;
            }
        }
        
        System.out.println("计算未读数量: " + unreadCount);
        
        result.put("unreadCount", unreadCount);
        result.put("totalCount", allNews.size());
        result.put("hasCommunity", true);
        
        return result;
    }

    @PostMapping("/community/read/{newsId}")
    public Map<String, Object> markAsRead(
            @PathVariable Long newsId,
            @RequestParam(defaultValue = "click") String source,
            @RequestParam(required = false) Integer duration,
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("currUser");
        
        System.out.println("标记已读请求 - newsId: " + newsId + ", user: " + (user != null ? user.getId() : "null"));
        
        if (user == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }

        CommunityNews news = communityNewsMapper.getById(newsId);
        if (news == null) {
            result.put("success", false);
            result.put("message", "新闻不存在");
            return result;
        }

        CommunityNewsRead record = new CommunityNewsRead();
        record.setUserId(user.getId());
        record.setCommunityNewsId(newsId);
        record.setReadTime(LocalDateTime.now());
        record.setReadSource(source);
        record.setReadDuration(duration != null ? duration : 0);

        try {
            int rows = communityNewsReadMapper.insertOrUpdate(record);
            System.out.println("标记已读成功 - 影响行数: " + rows);
            result.put("success", true);
            result.put("message", "已标记为已读");
        } catch (Exception e) {
            System.out.println("标记已读失败: " + e.getMessage());
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "标记失败：" + e.getMessage());
        }

        return result;
    }

    @PostMapping("/community/read/batch")
    public Map<String, Object> markBatchAsRead(
            @RequestParam String newsIds,
            @RequestParam(defaultValue = "voice") String source,
            @RequestParam(required = false) Integer duration,
            HttpSession session) {
        
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("currUser");
        
        if (user == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }

        String[] idArray = newsIds.split(",");
        int successCount = 0;

        for (String idStr : idArray) {
            try {
                Long newsId = Long.parseLong(idStr.trim());
                CommunityNewsRead record = new CommunityNewsRead();
                record.setUserId(user.getId());
                record.setCommunityNewsId(newsId);
                record.setReadTime(LocalDateTime.now());
                record.setReadSource(source);
                record.setReadDuration(duration != null ? duration : 0);
                communityNewsReadMapper.insertOrUpdate(record);
                successCount++;
            } catch (Exception e) {
                // 忽略单条失败
            }
        }

        result.put("success", true);
        result.put("markedCount", successCount);
        result.put("message", "成功标记 " + successCount + " 条新闻为已读");
        
        return result;
    }

    @GetMapping("/community/read/ids")
    public Map<String, Object> getReadNewsIds(HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("currUser");
        
        if (user == null) {
            result.put("success", false);
            result.put("message", "请先登录");
            return result;
        }

        List<Long> readIds = communityNewsReadMapper.getReadNewsIds(user.getId());
        result.put("success", true);
        result.put("readIds", readIds);
        
        return result;
    }


    @GetMapping("/community/read/check/{newsId}")
    public Map<String, Object> checkReadStatus(@PathVariable Long newsId, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        User user = (User) session.getAttribute("currUser");
        
        if (user == null) {
            result.put("isRead", false);
            return result;
        }

        CommunityNewsRead record = communityNewsReadMapper.getByUserAndNews(user.getId(), newsId);
        result.put("isRead", record != null);
        if (record != null) {
            result.put("readTime", record.getReadTime());
            result.put("readSource", record.getReadSource());
        }
        
        return result;
    }

}


package com.chat.older.xiechen.service;

import com.chat.older.xiechen.dao.CommunityNewsMapper;
import com.chat.older.xiechen.dao.CommunityNewsReadMapper;
import com.chat.older.xiechen.dao.NewsStatusChangeLogMapper;
import com.chat.older.xiechen.model.CommunityNews;
import com.chat.older.xiechen.model.NewsStatusChangeLog;
import com.chat.older.xiechen.model.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Service
public class CommunityNewsService {

    private static final Logger logger = LoggerFactory.getLogger(CommunityNewsService.class);

    @Autowired
    private CommunityNewsMapper communityNewsMapper;

    @Autowired
    private CommunityNewsReadMapper communityNewsReadMapper;

    @Autowired
    private NewsStatusChangeLogMapper newsStatusChangeLogMapper;

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> updateNewsWithStatusChange(Long id, Map<String, Object> params, 
            HttpSession session, HttpServletRequest request) {
        
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Map.of("code", 401, "msg", "未登录");
        }

        CommunityNews existing = communityNewsMapper.getByIdForAdmin(id);
        if (existing == null) {
            return Map.of("code", 404, "msg", "新闻不存在");
        }

        String title = (String) params.get("title");
        String content = (String) params.get("content");
        String category = (String) params.get("category");
        String coverImage = (String) params.get("coverImage");
        String pubType = (String) params.get("pubType");
        String scheduledTimeStr = (String) params.get("scheduledTime");

        if (title == null || title.trim().isEmpty()) {
            return Map.of("code", 400, "msg", "标题不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            return Map.of("code", 400, "msg", "内容不能为空");
        }
        if (category == null || category.trim().isEmpty()) {
            return Map.of("code", 400, "msg", "请选择分类");
        }

        Integer newStatus;
        LocalDateTime scheduledTime = null;

        if ("scheduled".equals(pubType) && scheduledTimeStr != null && !scheduledTimeStr.isEmpty()) {
            newStatus = 2;
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                scheduledTime = LocalDateTime.parse(scheduledTimeStr.replace("T", " "), formatter);
            } catch (Exception e) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                    scheduledTime = LocalDateTime.parse(scheduledTimeStr, formatter);
                } catch (Exception ex) {
                    return Map.of("code", 400, "msg", "时间格式错误");
                }
            }
        } else if ("draft".equals(pubType)) {
            newStatus = 0;
        } else {
            if (existing.getStatus() == 0 || existing.getStatus() == 2) {
                newStatus = 1;
            } else {
                newStatus = existing.getStatus();
            }
        }

        Integer oldStatus = existing.getStatus();
        int clearedReadCount = 0;

        if (oldStatus == 1 && newStatus == 0) {
            clearedReadCount = communityNewsReadMapper.countByNewsId(id);
            
            if (clearedReadCount > 0) {
                int deletedRows = communityNewsReadMapper.deleteByNewsId(id);
                logger.info("新闻状态从已发布变更为草稿，删除已读记录: newsId={}, 删除数量={}", id, deletedRows);
            }
        }

        CommunityNews news = new CommunityNews();
        news.setId(id);
        news.setTitle(title.trim());
        news.setContent(content);
        news.setCategory(category);
        news.setCoverImage(coverImage);
        news.setStatus(newStatus);
        news.setScheduledTime(scheduledTime);

        if (newStatus == 1 && (oldStatus == 0 || oldStatus == 2)) {
            news.setPublishTime(LocalDateTime.now());
        }

        communityNewsMapper.update(news);

        if (oldStatus != newStatus) {
            NewsStatusChangeLog log = new NewsStatusChangeLog();
            log.setNewsId(id);
            log.setNewsTitle(title.trim());
            log.setOldStatus(oldStatus);
            log.setNewStatus(newStatus);
            log.setOperatorId(currUser.getId());
            log.setOperatorName(currUser.getNickname() != null ? currUser.getNickname() : "管理员");
            log.setClearedReadCount(clearedReadCount);
            log.setOperateTime(LocalDateTime.now());
            log.setOperateIp(getClientIp(request));
            log.setRemark(buildRemark(oldStatus, newStatus, clearedReadCount));
            
            newsStatusChangeLogMapper.insert(log);
            logger.info("新闻状态变更日志已记录: newsId={}, oldStatus={}, newStatus={}, clearedReadCount={}", 
                    id, oldStatus, newStatus, clearedReadCount);
        }

        return Map.of("code", 200, "msg", "更新成功", "data", Map.of(
                "id", id,
                "oldStatus", oldStatus,
                "newStatus", newStatus,
                "clearedReadCount", clearedReadCount
        ));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> createNews(Map<String, Object> params, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Map.of("code", 401, "msg", "未登录");
        }

        String title = (String) params.get("title");
        String content = (String) params.get("content");
        String category = (String) params.get("category");
        String coverImage = (String) params.get("coverImage");
        String pubType = (String) params.get("pubType");
        String scheduledTimeStr = (String) params.get("scheduledTime");
        Integer communityId = (Integer) params.get("communityId");

        if (title == null || title.trim().isEmpty()) {
            return Map.of("code", 400, "msg", "标题不能为空");
        }
        if (content == null || content.trim().isEmpty()) {
            return Map.of("code", 400, "msg", "内容不能为空");
        }
        if (category == null || category.trim().isEmpty()) {
            return Map.of("code", 400, "msg", "请选择分类");
        }

        CommunityNews news = new CommunityNews();
        news.setCommunityId(communityId);
        news.setTitle(title.trim());
        news.setContent(content);
        news.setCategory(category);
        news.setCoverImage(coverImage);
        news.setPublisherId(currUser.getId());

        if ("scheduled".equals(pubType) && scheduledTimeStr != null && !scheduledTimeStr.isEmpty()) {
            news.setStatus(2);
            try {
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                LocalDateTime scheduledTime = LocalDateTime.parse(scheduledTimeStr.replace("T", " "), formatter);
                news.setScheduledTime(scheduledTime);
            } catch (Exception e) {
                try {
                    DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                    LocalDateTime scheduledTime = LocalDateTime.parse(scheduledTimeStr, formatter);
                    news.setScheduledTime(scheduledTime);
                } catch (Exception ex) {
                    return Map.of("code", 400, "msg", "时间格式错误");
                }
            }
        } else if ("draft".equals(pubType)) {
            news.setStatus(0);
        } else {
            news.setStatus(1);
            news.setPublishTime(LocalDateTime.now());
        }

        communityNewsMapper.insertForAdmin(news);

        return Map.of("code", 200, "msg", "创建成功", "data", Map.of("id", news.getId()));
    }

    @Transactional(rollbackFor = Exception.class)
    public Map<String, Object> deleteNews(Long id, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Map.of("code", 401, "msg", "未登录");
        }

        CommunityNews existing = communityNewsMapper.getByIdForAdmin(id);
        if (existing == null) {
            return Map.of("code", 404, "msg", "新闻不存在");
        }

        int clearedReadCount = communityNewsReadMapper.countByNewsId(id);
        if (clearedReadCount > 0) {
            communityNewsReadMapper.deleteByNewsId(id);
        }

        communityNewsMapper.deleteById(id);

        logger.info("新闻已删除: newsId={}, title={}, clearedReadCount={}, operator={}", 
                id, existing.getTitle(), clearedReadCount, currUser.getId());

        return Map.of("code", 200, "msg", "删除成功");
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private String buildRemark(Integer oldStatus, Integer newStatus, int clearedReadCount) {
        String oldStatusText = getStatusText(oldStatus);
        String newStatusText = getStatusText(newStatus);
        StringBuilder remark = new StringBuilder();
        remark.append("状态从[").append(oldStatusText).append("]变更为[").append(newStatusText).append("]");
        if (clearedReadCount > 0) {
            remark.append("，清除已读记录").append(clearedReadCount).append("条");
        }
        return remark.toString();
    }

    private String getStatusText(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "草稿";
            case 1 -> "已发布";
            case 2 -> "定时发布";
            default -> "未知";
        };
    }
}

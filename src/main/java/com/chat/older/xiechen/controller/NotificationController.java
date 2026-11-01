package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.FriendMapper;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.service.MedicineAssistantService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notification")
public class NotificationController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private MedicineAssistantService medicineAssistantService;

    private User getLoginUser(HttpSession session) {
        return (User) session.getAttribute("currUser");
    }

    @GetMapping("/list")
    public Result getNotifications(HttpSession session,
                                   @RequestParam(required = false) String type,
                                   @RequestParam(defaultValue = "20") int limit) {
        User user = getLoginUser(session);
        if (user == null) return Result.error("请先登录");

        String sql = "SELECT * FROM notifications WHERE user_id = ? ";
        if (type != null && !type.isEmpty()) {
            sql += "AND type = ? ";
        }
        sql += "ORDER BY create_time DESC LIMIT ?";

        List<Map<String, Object>> notifications;
        if (type != null && !type.isEmpty()) {
            notifications = jdbcTemplate.queryForList(sql, user.getId(), type, limit);
        } else {
            notifications = jdbcTemplate.queryForList(sql, user.getId(), limit);
        }

        return Result.success(notifications);
    }

    @GetMapping("/unread-count")
    public Result getUnreadCount(HttpSession session) {
        User user = getLoginUser(session);
        if (user == null) return Result.error("请先登录");

        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0",
            Integer.class, user.getId()
        );

        return Result.success(count != null ? count : 0);
    }

    @PostMapping("/mark-read/{id}")
    public Result markAsRead(@PathVariable Long id, HttpSession session) {
        User user = getLoginUser(session);
        if (user == null) return Result.error("请先登录");

        int updated = jdbcTemplate.update(
            "UPDATE notifications SET is_read = 1 WHERE id = ? AND user_id = ?",
            id, user.getId()
        );

        return updated > 0 ? Result.success("已标记为已读") : Result.error("操作失败");
    }

    @PostMapping("/mark-all-read")
    public Result markAllAsRead(HttpSession session,
                                @RequestParam(required = false) String type) {
        User user = getLoginUser(session);
        if (user == null) return Result.error("请先登录");

        String sql = "UPDATE notifications SET is_read = 1 WHERE user_id = ? AND is_read = 0";
        int updated;

        if (type != null && !type.isEmpty()) {
            updated = jdbcTemplate.update(sql + " AND type = ?", user.getId(), type);
        } else {
            updated = jdbcTemplate.update(sql, user.getId());
        }

        return Result.success("已标记 " + updated + " 条通知为已读");
    }

    @GetMapping("/anomaly-summary")
    public Result getAnomalySummary(HttpSession session) {
        User user = getLoginUser(session);
        if (user == null) return Result.error("请先登录");

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(user.getId());
        
        int totalMissed = 0;
        int totalOverdose = 0;
        
        for (Map<String, Object> friend : friends) {
            Long friendId = ((Number) friend.get("id")).longValue();
            
            Integer missed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medication_logs ml " +
                "LEFT JOIN user_medicines um ON ml.user_medicine_id = um.id " +
                "WHERE um.user_id = ? AND ml.status = 'missed' " +
                "AND (ml.is_read IS NULL OR ml.is_read = 0) " +
                "AND DATE(ml.scheduled_time) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)",
                Integer.class, friendId
            );
            
            Integer overdose = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medication_logs ml " +
                "LEFT JOIN user_medicines um ON ml.user_medicine_id = um.id " +
                "WHERE um.user_id = ? AND ml.status = 'overdose' " +
                "AND (ml.is_read IS NULL OR ml.is_read = 0) " +
                "AND DATE(ml.taken_time) >= DATE_SUB(CURDATE(), INTERVAL 7 DAY)",
                Integer.class, friendId
            );
            
            totalMissed += missed != null ? missed : 0;
            totalOverdose += overdose != null ? overdose : 0;
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("missed", totalMissed);
        summary.put("overdose", totalOverdose);
        summary.put("total", totalMissed + totalOverdose);

        return Result.success(summary);
    }

    @GetMapping("/alerts")
    public Result getFamilyAlerts(HttpSession session) {
        User user = getLoginUser(session);
        if (user == null) return Result.error("请先登录");

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(user.getId());
        
        int totalMissed = 0;
        int totalOverdose = 0;
        int totalLowStock = 0;
        
        for (Map<String, Object> friend : friends) {
            Long friendId = ((Number) friend.get("id")).longValue();
            
            Integer missed = getAlertCount(friendId, "missed");
            Integer overdose = getAlertCount(friendId, "overdose");
            Integer lowStock = getAlertCount(friendId, "low_stock");
            
            totalMissed += missed != null ? missed : 0;
            totalOverdose += overdose != null ? overdose : 0;
            totalLowStock += lowStock != null ? lowStock : 0;
        }

        Map<String, Object> summary = new HashMap<>();
        summary.put("missed", totalMissed);
        summary.put("overdose", totalOverdose);
        summary.put("lowStock", totalLowStock);
        summary.put("total", totalMissed + totalOverdose + totalLowStock);

        return Result.success(summary);
    }

    @GetMapping("/alerts/detail")
    public Result getAlertDetails(HttpSession session) {
        User user = getLoginUser(session);
        if (user == null) return Result.error("请先登录");

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(user.getId());
        
        java.util.List<Map<String, Object>> allAlerts = new java.util.ArrayList<>();
        
        for (Map<String, Object> friend : friends) {
            Long friendId = ((Number) friend.get("id")).longValue();
            String friendName = (String) friend.get("nickname");
            
            List<Map<String, Object>> alerts = jdbcTemplate.queryForList(
                "SELECT a.*, um.custom_name as medicine_name, u.nickname as elder_name " +
                "FROM medicine_stock_alert a " +
                "JOIN user_medicines um ON a.user_medicine_id = um.id " +
                "JOIN users u ON um.user_id = u.id " +
                "WHERE um.user_id = ? AND a.is_resolved = 0 " +
                "AND a.alert_type IN ('missed', 'overdose', 'low_stock') " +
                "AND a.create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
                "ORDER BY a.alert_level DESC, a.create_time DESC",
                friendId
            );
            
            for (Map<String, Object> alert : alerts) {
                alert.put("friendName", friendName);
                allAlerts.add(alert);
            }
        }

        return Result.success(allAlerts);
    }

    @PostMapping("/alerts/mark-read/{alertId}")
    public Result markAlertAsRead(@PathVariable Long alertId, HttpSession session) {
        User user = getLoginUser(session);
        if (user == null) return Result.error("请先登录");

        int updated = jdbcTemplate.update(
            "UPDATE medicine_stock_alert SET is_read = 1 WHERE id = ?",
            alertId
        );

        return updated > 0 ? Result.success("已标记为已读") : Result.error("操作失败");
    }

    @PostMapping("/alerts/mark-all-read")
    public Result markAllAlertsAsRead(HttpSession session) {
        User user = getLoginUser(session);
        if (user == null) return Result.error("请先登录");

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(user.getId());
        int totalUpdated = 0;
        
        for (Map<String, Object> friend : friends) {
            Long friendId = ((Number) friend.get("id")).longValue();
            
            int updated = jdbcTemplate.update(
                "UPDATE medicine_stock_alert a " +
                "JOIN user_medicines um ON a.user_medicine_id = um.id " +
                "SET a.is_read = 1 " +
                "WHERE um.user_id = ? AND a.is_read = 0 AND a.is_resolved = 0",
                friendId
            );
            totalUpdated += updated;
        }

        return Result.success("已标记 " + totalUpdated + " 条预警为已读");
    }

    @PostMapping("/alerts/resolve/{alertId}")
    public Result resolveAlert(@PathVariable Long alertId, HttpSession session) {
        User user = getLoginUser(session);
        if (user == null) return Result.error("请先登录");

        int updated = jdbcTemplate.update(
            "UPDATE medicine_stock_alert SET is_resolved = 1, resolved_time = NOW() WHERE id = ?",
            alertId
        );

        return updated > 0 ? Result.success("已处理") : Result.error("操作失败");
    }

    @Scheduled(fixedRate = 3600000)
    public void cleanupOldAlerts() {
        int deleted = jdbcTemplate.update(
            "UPDATE medicine_stock_alert SET is_resolved = 1, resolved_time = NOW() " +
            "WHERE is_resolved = 0 AND create_time < DATE_SUB(NOW(), INTERVAL 24 HOUR) " +
            "AND alert_type IN ('missed', 'overdose')"
        );
        if (deleted > 0) {
            System.out.println("[定时清理] 已自动清除 " + deleted + " 条超过24小时的服药异常预警");
        }
    }

    private Integer getAlertCount(Long userId, String alertType) {
        try {
            return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medicine_stock_alert a " +
                "JOIN user_medicines um ON a.user_medicine_id = um.id " +
                "WHERE um.user_id = ? AND a.alert_type = ? AND a.is_resolved = 0 " +
                "AND a.is_read = 0 AND a.create_time >= DATE_SUB(NOW(), INTERVAL 24 HOUR)",
                Integer.class, userId, alertType
            );
        } catch (Exception e) {
            return 0;
        }
    }
}

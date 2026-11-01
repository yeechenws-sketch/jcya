package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.AdminMapper;
import com.chat.older.xiechen.dao.CommunityNewsMapper;
import com.chat.older.xiechen.model.User;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    @Autowired
    private AdminMapper adminMapper;

    @Autowired
    private CommunityNewsMapper communityNewsMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/overview")
    public Result<Map<String, Object>> getOverview(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        Map<String, Object> result = new HashMap<>();
        String today = LocalDate.now().toString();

        Integer elderTotal = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_community WHERE community_id = ? AND status = 1 AND move_out_time IS NULL",
            Integer.class, communityId);
        result.put("elderTotal", elderTotal != null ? elderTotal : 0);

        Integer todayTaken = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM medication_logs ml " +
            "JOIN user_medicines um ON ml.user_medicine_id = um.id " +
            "JOIN user_community uc ON um.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0 AND DATE(ml.scheduled_time) = ? AND ml.status IN ('taken', 'taken_early', 'taken_makeup')",
            Integer.class, communityId, today);
        result.put("todayTaken", todayTaken != null ? todayTaken : 0);

        Integer todayShouldTake = jdbcTemplate.queryForObject(
            "SELECT COALESCE(SUM(um.frequency_per_day), 0) FROM user_medicines um " +
            "JOIN user_community uc ON um.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0",
            Integer.class, communityId);
        result.put("todayShouldTake", todayShouldTake != null ? todayShouldTake : 0);

        int shouldTake = todayShouldTake != null ? todayShouldTake : 0;
        int taken = todayTaken != null ? todayTaken : 0;
        double medicationRate = shouldTake > 0 ? (double) taken / shouldTake * 100 : 0;
        result.put("medicationRate", Math.round(medicationRate * 10) / 10.0);

        Integer missedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT um.user_id) FROM user_medicines um " +
            "JOIN user_community uc ON um.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0 " +
            "AND EXISTS (" +
            "  SELECT 1 FROM medication_logs ml " +
            "  WHERE ml.user_medicine_id = um.id AND DATE(ml.scheduled_time) = ? AND ml.status = 'missed'" +
            ")",
            Integer.class, communityId, today);
        result.put("missedCount", missedCount != null ? missedCount : 0);

        Integer lowStockCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT msa.user_medicine_id) FROM medicine_stock_alert msa " +
            "JOIN user_medicines um ON msa.user_medicine_id = um.id " +
            "JOIN user_community uc ON um.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0 " +
            "AND msa.alert_type = 'low_stock' AND msa.is_resolved = 0",
            Integer.class, communityId);
        result.put("lowStockCount", lowStockCount != null ? lowStockCount : 0);

        Integer healthAbnormalCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT ha.user_id) FROM health_alerts ha " +
            "JOIN user_community uc ON ha.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND DATE(ha.create_time) = ? AND ha.alert_level > 0",
            Integer.class, communityId, today);
        result.put("healthAbnormalCount", healthAbnormalCount != null ? healthAbnormalCount : 0);

        Integer emergencyCallCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM emergency_call_records ecr " +
            "JOIN user_community uc ON ecr.elder_user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND DATE(ecr.create_time) = ?",
            Integer.class, communityId, today);
        result.put("emergencyCallCount", emergencyCallCount != null ? emergencyCallCount : 0);

        return Result.success(result);
    }

    @GetMapping("/medication/trend")
    public Result<List<Map<String, Object>>> getMedicationTrend(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        List<Map<String, Object>> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        for (int i = 6; i >= 0; i--) {
            LocalDate date = LocalDate.now().minusDays(i);
            String dateStr = date.toString();
            String displayDate = date.format(formatter);

            Integer taken = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM medication_logs ml " +
                "JOIN user_medicines um ON ml.user_medicine_id = um.id " +
                "JOIN user_community uc ON um.user_id = uc.user_id " +
                "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0 AND DATE(ml.scheduled_time) = ? AND ml.status IN ('taken', 'taken_early', 'taken_makeup')",
                Integer.class, communityId, dateStr);
            
            Map<String, Object> item = new HashMap<>();
            item.put("date", displayDate);
            item.put("taken", taken != null ? taken : 0);
            result.add(item);
        }

        return Result.success(result);
    }

    @GetMapping("/medication/pie")
    public Result<Map<String, Object>> getMedicationPie(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        String today = LocalDate.now().toString();

        Integer onTimeCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM medication_logs ml " +
            "JOIN user_medicines um ON ml.user_medicine_id = um.id " +
            "JOIN user_community uc ON um.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0 AND DATE(ml.scheduled_time) = ? " +
            "AND ml.status = 'taken' AND (ml.take_result = 'normal' OR ml.take_result IS NULL)",
            Integer.class, communityId, today);
        
        Integer missedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM medication_logs ml " +
            "JOIN user_medicines um ON ml.user_medicine_id = um.id " +
            "JOIN user_community uc ON um.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0 AND DATE(ml.scheduled_time) = ? " +
            "AND ml.status = 'missed'",
            Integer.class, communityId, today);
        
        Integer earlyCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM medication_logs ml " +
            "JOIN user_medicines um ON ml.user_medicine_id = um.id " +
            "JOIN user_community uc ON um.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0 AND DATE(ml.scheduled_time) = ? " +
            "AND ml.status IN ('taken_early', 'taken_makeup')",
            Integer.class, communityId, today);

        Map<String, Object> result = new HashMap<>();
        result.put("onTime", onTimeCount != null ? onTimeCount : 0);
        result.put("missed", missedCount != null ? missedCount : 0);
        result.put("early", earlyCount != null ? earlyCount : 0);

        return Result.success(result);
    }

    @GetMapping("/medication/low-stock")
    public Result<List<Map<String, Object>>> getLowStockList(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT up.real_name as elderName, um.custom_name as medicineName, " +
                "um.current_stock as currentStock, um.dosage_per_time as dosagePerTime, " +
                "um.frequency_per_day as frequencyPerDay, " +
                "msa.days_remaining as daysRemaining, msa.message as alertMessage, msa.create_time as alertTime " +
                "FROM medicine_stock_alert msa " +
                "JOIN user_medicines um ON msa.user_medicine_id = um.id " +
                "JOIN user_profiles up ON um.user_id = up.user_id " +
                "JOIN user_community uc ON um.user_id = uc.user_id " +
                "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0 " +
                "AND msa.alert_type = 'low_stock' AND msa.is_resolved = 0 " +
                "AND msa.id = (" +
                "  SELECT MAX(id) FROM medicine_stock_alert " +
                "  WHERE user_medicine_id = msa.user_medicine_id " +
                "  AND alert_type = 'low_stock' AND is_resolved = 0" +
                ") " +
                "ORDER BY msa.days_remaining ASC, msa.create_time DESC LIMIT 10",
                communityId);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/health/abnormal")
    public Result<Map<String, Object>> getHealthAbnormal(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        String today = LocalDate.now().toString();

        Integer bpAbnormal = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT ha.user_id) FROM health_alerts ha " +
            "JOIN user_community uc ON ha.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND DATE(ha.create_time) = ? " +
            "AND ha.alert_type = 'BP' AND ha.alert_level > 0",
            Integer.class, communityId, today);

        Integer hrAbnormal = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT ha.user_id) FROM health_alerts ha " +
            "JOIN user_community uc ON ha.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND DATE(ha.create_time) = ? " +
            "AND ha.alert_type = 'HR' AND ha.alert_level > 0",
            Integer.class, communityId, today);

        Integer boAbnormal = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT ha.user_id) FROM health_alerts ha " +
            "JOIN user_community uc ON ha.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND DATE(ha.create_time) = ? " +
            "AND ha.alert_type = 'BO' AND ha.alert_level > 0",
            Integer.class, communityId, today);

        Integer bgAbnormal = jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT ha.user_id) FROM health_alerts ha " +
            "JOIN user_community uc ON ha.user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND DATE(ha.create_time) = ? " +
            "AND ha.alert_type = 'BG' AND ha.alert_level > 0",
            Integer.class, communityId, today);

        Map<String, Object> result = new HashMap<>();
        result.put("bpAbnormal", bpAbnormal != null ? bpAbnormal : 0);
        result.put("hrAbnormal", hrAbnormal != null ? hrAbnormal : 0);
        result.put("boAbnormal", boAbnormal != null ? boAbnormal : 0);
        result.put("bgAbnormal", bgAbnormal != null ? bgAbnormal : 0);

        return Result.success(result);
    }

    @GetMapping("/health/recent")
    public Result<List<Map<String, Object>>> getRecentHealthData(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT up.real_name as elderName, hvs.type as dataType, " +
                "hvs.value1, hvs.value2, hvs.create_time as recordTime " +
                "FROM health_vital_signs hvs " +
                "JOIN user_profiles up ON hvs.user_id = up.user_id " +
                "JOIN user_community uc ON hvs.user_id = uc.user_id " +
                "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL " +
                "ORDER BY hvs.create_time DESC LIMIT 10",
                communityId);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/emergency/recent")
    public Result<List<Map<String, Object>>> getRecentEmergencyCalls(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT ecr.elder_user_name as elderName, ecr.call_type as callType, " +
                "ecr.status, ecr.start_time as callTime, ecr.answered_by_user_name as answeredBy " +
                "FROM emergency_call_records ecr " +
                "JOIN user_community uc ON ecr.elder_user_id = uc.user_id " +
                "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL " +
                "ORDER BY ecr.start_time DESC LIMIT 10",
                communityId);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/emergency/stats")
    public Result<Map<String, Object>> getEmergencyStats(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        String today = LocalDate.now().toString();

        Integer todayTotal = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM emergency_call_records ecr " +
            "JOIN user_community uc ON ecr.elder_user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND DATE(ecr.create_time) = ?",
            Integer.class, communityId, today);

        Integer todayAnswered = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM emergency_call_records ecr " +
            "JOIN user_community uc ON ecr.elder_user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND DATE(ecr.create_time) = ? AND ecr.status = 'answered'",
            Integer.class, communityId, today);
        
        Integer todayCancelled = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM emergency_call_records ecr " +
            "JOIN user_community uc ON ecr.elder_user_id = uc.user_id " +
            "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND DATE(ecr.create_time) = ? AND ecr.status = 'cancelled'",
            Integer.class, communityId, today);

        Map<String, Object> result = new HashMap<>();
        result.put("todayTotal", todayTotal != null ? todayTotal : 0);
        result.put("todayAnswered", todayAnswered != null ? todayAnswered : 0);
        result.put("todayCancelled", todayCancelled != null ? todayCancelled : 0);

        return Result.success(result);
    }

    @GetMapping("/news/stats")
    public Result<Map<String, Object>> getNewsStats(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        String today = LocalDate.now().toString();

        Integer todayPublished = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM community_news WHERE community_id = ? " +
            "AND status = 1 AND DATE(publish_time) = ?",
            Integer.class, communityId, today);

        Integer totalNews = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM community_news WHERE community_id = ? AND status = 1",
            Integer.class, communityId);

        Integer readCount = 0;
        try {
            readCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT cnr.user_id) FROM community_news_read cnr " +
                "JOIN community_news cn ON cnr.community_news_id = cn.id " +
                "JOIN user_community uc ON cnr.user_id = uc.user_id " +
                "WHERE cn.community_id = ? AND uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL",
                Integer.class, communityId, communityId);
        } catch (Exception e) {
            readCount = 0;
        }

        Integer elderCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_community WHERE community_id = ? AND status = 1 AND move_out_time IS NULL",
            Integer.class, communityId);

        int total = totalNews != null ? totalNews : 0;
        int elders = elderCount != null ? elderCount : 0;
        int reads = readCount != null ? readCount : 0;
        int unreadCount = elders * total - reads;
        if (unreadCount < 0) unreadCount = 0;

        Map<String, Object> result = new HashMap<>();
        result.put("todayPublished", todayPublished != null ? todayPublished : 0);
        result.put("totalNews", total);
        result.put("readCount", reads);
        result.put("unreadCount", unreadCount);

        return Result.success(result);
    }

    @GetMapping("/news/list")
    public Result<List<Map<String, Object>>> getNewsList(
            @RequestParam(defaultValue = "5") Integer limit,
            HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT id, title, category, publish_time as publishTime " +
                "FROM community_news WHERE community_id = ? AND status = 1 " +
                "ORDER BY publish_time DESC LIMIT ?",
                communityId, limit);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/detail/elders")
    public Result<List<Map<String, Object>>> getElderList(
            @RequestParam(defaultValue = "20") Integer limit,
            HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT up.user_id as userId, up.real_name as elderName, up.gender, up.age, " +
                "up.user_phone as phone, up.address_detail as address, uc.move_in_time as moveInTime, " +
                "n.neighborhood_name as neighborhoodName " +
                "FROM user_profiles up " +
                "JOIN user_community uc ON up.user_id = uc.user_id " +
                "LEFT JOIN neighborhoods n ON up.neighborhood_id = n.id " +
                "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL " +
                "ORDER BY uc.move_in_time DESC LIMIT ?",
                communityId, limit);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/detail/missed-medication")
    public Result<List<Map<String, Object>>> getMissedMedicationList(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        String today = LocalDate.now().toString();

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT up.real_name as elderName, um.custom_name as medicineName, " +
                "ml.scheduled_time as scheduledTime, um.dosage_per_time as dosage, " +
                "um.dose_unit as unit, ml.status " +
                "FROM medication_logs ml " +
                "JOIN user_medicines um ON ml.user_medicine_id = um.id " +
                "JOIN user_profiles up ON um.user_id = up.user_id " +
                "JOIN user_community uc ON um.user_id = uc.user_id " +
                "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0 " +
                "AND DATE(ml.scheduled_time) = ? AND ml.status = 'missed' " +
                "ORDER BY ml.scheduled_time DESC LIMIT 50",
                communityId, today);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/detail/low-stock")
    public Result<List<Map<String, Object>>> getLowStockDetailList(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT up.real_name as elderName, um.custom_name as medicineName, " +
                "um.current_stock as currentStock, um.dosage_per_time as dosagePerTime, " +
                "um.dose_unit as unit, um.frequency_per_day as frequencyPerDay, " +
                "msa.days_remaining as daysRemaining, msa.message as alertMessage " +
                "FROM medicine_stock_alert msa " +
                "JOIN user_medicines um ON msa.user_medicine_id = um.id " +
                "JOIN user_profiles up ON um.user_id = up.user_id " +
                "JOIN user_community uc ON um.user_id = uc.user_id " +
                "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND um.is_deleted = 0 " +
                "AND msa.alert_type = 'low_stock' AND msa.is_resolved = 0 " +
                "AND msa.id = (" +
                "  SELECT MAX(id) FROM medicine_stock_alert " +
                "  WHERE user_medicine_id = msa.user_medicine_id " +
                "  AND alert_type = 'low_stock' AND is_resolved = 0" +
                ") " +
                "ORDER BY msa.days_remaining ASC LIMIT 50",
                communityId);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/detail/health-abnormal")
    public Result<List<Map<String, Object>>> getHealthAbnormalList(
            @RequestParam(required = false) String type,
            HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        String today = LocalDate.now().toString();
        String typeCondition = "";
        if (type != null && !type.isEmpty()) {
            typeCondition = "AND ha.alert_type = '" + type + "'";
        }

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT up.real_name as elderName, ha.alert_type as alertType, " +
                "ha.alert_level as alertLevel, ha.current_value as currentValue, " +
                "ha.threshold_value as thresholdValue, ha.create_time as alertTime " +
                "FROM health_alerts ha " +
                "JOIN user_profiles up ON ha.user_id = up.user_id " +
                "JOIN user_community uc ON ha.user_id = uc.user_id " +
                "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL " +
                "AND DATE(ha.create_time) = ? AND ha.alert_level > 0 " + typeCondition +
                "ORDER BY ha.alert_level DESC, ha.create_time DESC LIMIT 50",
                communityId, today);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/detail/emergency-calls")
    public Result<List<Map<String, Object>>> getEmergencyCallList(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        String today = LocalDate.now().toString();

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT ecr.elder_user_name as elderName, ecr.call_type as callType, " +
                "ecr.status, ecr.start_time as callTime, ecr.end_time as endTime, " +
                "ecr.answered_by_user_name as answeredBy, ecr.duration " +
                "FROM emergency_call_records ecr " +
                "JOIN user_community uc ON ecr.elder_user_id = uc.user_id " +
                "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL AND DATE(ecr.create_time) = ? " +
                "ORDER BY ecr.create_time DESC LIMIT 50",
                communityId, today);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/news/detail")
    public Result<Map<String, Object>> getNewsDetail(
            @RequestParam Long newsId,
            HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        try {
            Map<String, Object> news = jdbcTemplate.queryForMap(
                "SELECT id, title, content, category, cover_image as coverImage, " +
                "view_count as viewCount, like_count as likeCount, publish_time as publishTime " +
                "FROM community_news WHERE id = ? AND community_id = ? AND status = 1",
                newsId, communityId);

            Integer readCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT cnr.user_id) FROM community_news_read cnr " +
                "JOIN user_community uc ON cnr.user_id = uc.user_id " +
                "WHERE cnr.community_news_id = ? AND uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL",
                Integer.class, newsId, communityId);

            Integer elderCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_community WHERE community_id = ? AND status = 1 AND move_out_time IS NULL",
                Integer.class, communityId);

            int reads = readCount != null ? readCount : 0;
            int elders = elderCount != null ? elderCount : 0;
            int unreadCount = elders - reads;
            if (unreadCount < 0) unreadCount = 0;

            news.put("readCount", reads);
            news.put("unreadCount", unreadCount);
            news.put("totalElders", elders);

            return Result.success(news);
        } catch (Exception e) {
            return Result.error("新闻不存在或无权访问");
        }
    }

    @GetMapping("/news/read-users")
    public Result<List<Map<String, Object>>> getNewsReadUsers(
            @RequestParam Long newsId,
            HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT up.real_name as userName, up.user_phone as phone, cnr.read_time as readTime " +
                "FROM community_news_read cnr " +
                "JOIN user_profiles up ON cnr.user_id = up.user_id " +
                "JOIN user_community uc ON cnr.user_id = uc.user_id " +
                "WHERE cnr.community_news_id = ? AND uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL " +
                "ORDER BY cnr.read_time DESC",
                newsId, communityId);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }

    @GetMapping("/news/unread-users")
    public Result<List<Map<String, Object>>> getNewsUnreadUsers(
            @RequestParam Long newsId,
            HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }

        Integer communityId = adminMapper.selectCommunityIdByAdminUserId(currUser.getId());
        if (communityId == null) {
            return Result.error("未找到管理的社区");
        }

        try {
            List<Map<String, Object>> list = jdbcTemplate.queryForList(
                "SELECT up.real_name as userName, up.user_phone as phone " +
                "FROM user_community uc " +
                "JOIN user_profiles up ON uc.user_id = up.user_id " +
                "WHERE uc.community_id = ? AND uc.status = 1 AND uc.move_out_time IS NULL " +
                "AND uc.user_id NOT IN (" +
                "  SELECT user_id FROM community_news_read WHERE community_news_id = ?" +
                ") " +
                "ORDER BY up.real_name",
                communityId, newsId);
            return Result.success(list);
        } catch (Exception e) {
            return Result.success(new ArrayList<>());
        }
    }
}

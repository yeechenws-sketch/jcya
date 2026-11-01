package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.service.SystemConfigService;
import com.chat.older.xiechen.service.UserManageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminManageController {

    @Autowired
    private UserManageService userManageService;
    
    @Autowired
    private SystemConfigService systemConfigService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @GetMapping("/users")
    public ResponseEntity<Map<String, Object>> getAllUsers() {
        List<User> users = userManageService.getAllUsers();
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("items", users);
        result.put("total", users.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users/full")
    public ResponseEntity<Map<String, Object>> getAllUsersFull() {
        List<Map<String, Object>> users = jdbcTemplate.queryForList(
            "SELECT u.id, u.username, u.password, u.nickname, u.avatar, u.role, u.create_time, " +
            "up.real_name, up.gender, up.age, up.user_phone, up.address_detail, up.custom_history, up.neighborhood_id, " +
            "n.neighborhood_name, n.community_id, c.community_name " +
            "FROM users u " +
            "LEFT JOIN user_profiles up ON u.id = up.user_id " +
            "LEFT JOIN neighborhoods n ON up.neighborhood_id = n.id " +
            "LEFT JOIN communities c ON n.community_id = c.id " +
            "ORDER BY u.id ASC"
        );
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("items", users);
        result.put("total", users.size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUserById(@PathVariable Long id) {
        User user = userManageService.getUserById(id);
        Map<String, Object> result = new HashMap<>();
        if (user != null) {
            result.put("success", true);
            result.put("user", user);
        } else {
            result.put("success", false);
            result.put("message", "用户不存在");
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/users/{id}/full")
    public ResponseEntity<Map<String, Object>> getUserFullById(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, Object> user = jdbcTemplate.queryForMap(
                "SELECT u.id, u.username, u.password, u.nickname, u.avatar, u.role, u.create_time, " +
                "up.real_name, up.gender, up.age, up.user_phone, up.address_detail, up.custom_history, up.neighborhood_id, " +
                "n.neighborhood_name " +
                "FROM users u " +
                "LEFT JOIN user_profiles up ON u.id = up.user_id " +
                "LEFT JOIN neighborhoods n ON up.neighborhood_id = n.id " +
                "WHERE u.id = ?",
                id
            );
            result.put("success", true);
            result.put("user", user);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "用户不存在");
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users")
    public ResponseEntity<Map<String, Object>> createUser(@RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (userManageService.getUserByUsername(user.getUsername()) != null) {
                result.put("success", false);
                result.put("message", "用户名已存在");
                return ResponseEntity.ok(result);
            }
            userManageService.createUser(user);
            result.put("success", true);
            result.put("message", "创建成功");
            result.put("user", user);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(@PathVariable Long id, @RequestBody User user) {
        Map<String, Object> result = new HashMap<>();
        try {
            user.setId(id);
            userManageService.updateUser(user);
            result.put("success", true);
            result.put("message", "更新成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/users/{id}/full")
    public ResponseEntity<Map<String, Object>> updateUserFull(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String nickname = (String) body.get("nickname");
            String role = (String) body.get("role");
            String avatar = (String) body.get("avatar");
            String password = (String) body.get("password");
            
            jdbcTemplate.update(
                "UPDATE users SET nickname = ?, role = ?, avatar = ? WHERE id = ?",
                nickname, role, avatar, id
            );
            
            if (password != null && !password.isEmpty()) {
                jdbcTemplate.update(
                    "UPDATE users SET password = MD5(?) WHERE id = ?",
                    password, id
                );
            }
            
            String realName = (String) body.get("real_name");
            String gender = (String) body.get("gender");
            Integer age = body.get("age") != null ? ((Number) body.get("age")).intValue() : null;
            String userPhone = (String) body.get("user_phone");
            String addressDetail = (String) body.get("address_detail");
            String customHistory = (String) body.get("custom_history");
            Integer neighborhoodId = body.get("neighborhood_id") != null ? ((Number) body.get("neighborhood_id")).intValue() : null;
            
            Integer profileExists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_profiles WHERE user_id = ?",
                Integer.class, id
            );
            
            if (profileExists != null && profileExists > 0) {
                jdbcTemplate.update(
                    "UPDATE user_profiles SET real_name = ?, gender = ?, age = ?, user_phone = ?, " +
                    "address_detail = ?, custom_history = ?, neighborhood_id = ? WHERE user_id = ?",
                    realName, gender, age, userPhone, addressDetail, customHistory, neighborhoodId, id
                );
            } else {
                jdbcTemplate.update(
                    "INSERT INTO user_profiles (user_id, real_name, gender, age, user_phone, address_detail, custom_history, neighborhood_id) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                    id, realName, gender, age, userPhone, addressDetail, customHistory, neighborhoodId
                );
            }
            
            if (neighborhoodId != null) {
                Integer communityId = jdbcTemplate.queryForObject(
                    "SELECT community_id FROM neighborhoods WHERE id = ?",
                    Integer.class, neighborhoodId
                );
                
                Integer existingCommunity = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM user_community WHERE user_id = ? AND status = 1",
                    Integer.class, id
                );
                
                if (existingCommunity != null && existingCommunity > 0) {
                    jdbcTemplate.update(
                        "UPDATE user_community SET community_id = ? WHERE user_id = ? AND status = 1",
                        communityId, id
                    );
                } else {
                    jdbcTemplate.update(
                        "INSERT INTO user_community (user_id, community_id, status, move_in_time) VALUES (?, ?, 1, NOW())",
                        id, communityId
                    );
                }
            }
            
            result.put("success", true);
            result.put("message", "更新成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "更新失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PostMapping("/users/full")
    public ResponseEntity<Map<String, Object>> createUserFull(@RequestBody Map<String, Object> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String username = (String) body.get("username");
            String password = (String) body.get("password");
            String nickname = (String) body.get("nickname");
            String role = (String) body.get("role");
            String avatar = (String) body.get("avatar");
            
            if (username == null || username.isEmpty()) {
                result.put("success", false);
                result.put("message", "用户名不能为空");
                return ResponseEntity.ok(result);
            }
            
            Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class, username
            );
            if (exists != null && exists > 0) {
                result.put("success", false);
                result.put("message", "用户名已存在");
                return ResponseEntity.ok(result);
            }
            
            if (password == null || password.isEmpty()) {
                password = "123456";
            }
            
            jdbcTemplate.update(
                "INSERT INTO users (username, password, nickname, role, avatar) VALUES (?, MD5(?), ?, ?, ?)",
                username, password, nickname, role, avatar
            );
            
            Long newId = jdbcTemplate.queryForObject(
                "SELECT LAST_INSERT_ID()",
                Long.class
            );
            
            String realName = (String) body.get("real_name");
            String gender = (String) body.get("gender");
            Integer age = body.get("age") != null ? ((Number) body.get("age")).intValue() : null;
            String userPhone = (String) body.get("user_phone");
            String addressDetail = (String) body.get("address_detail");
            String customHistory = (String) body.get("custom_history");
            Integer neighborhoodId = body.get("neighborhood_id") != null ? ((Number) body.get("neighborhood_id")).intValue() : null;
            
            jdbcTemplate.update(
                "INSERT INTO user_profiles (user_id, real_name, gender, age, user_phone, address_detail, custom_history, neighborhood_id) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)",
                newId, realName, gender, age, userPhone, addressDetail, customHistory, neighborhoodId
            );
            
            if (neighborhoodId != null) {
                Integer communityId = jdbcTemplate.queryForObject(
                    "SELECT community_id FROM neighborhoods WHERE id = ?",
                    Integer.class, neighborhoodId
                );
                jdbcTemplate.update(
                    "INSERT INTO user_community (user_id, community_id, status, move_in_time) VALUES (?, ?, 1, NOW())",
                    newId, communityId
                );
            }
            
            result.put("success", true);
            result.put("message", "创建成功");
            result.put("id", newId);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @PutMapping("/users/{id}/password")
    public ResponseEntity<Map<String, Object>> updatePassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String newPassword = body.get("password");
            if (newPassword == null || newPassword.length() < 4) {
                result.put("success", false);
                result.put("message", "密码长度至少4位");
                return ResponseEntity.ok(result);
            }
            userManageService.updatePassword(id, newPassword);
            result.put("success", true);
            result.put("message", "密码更新成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "密码更新失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> deleteUser(@PathVariable Long id) {
        Map<String, Object> result = new HashMap<>();
        try {
            jdbcTemplate.update("DELETE FROM comment_like WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM community_news_read WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM daily_activities WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM emergency_call_records WHERE elder_user_id = ? OR answered_by_user_id = ?", id, id);
            
            jdbcTemplate.update("DELETE FROM emergency_contacts WHERE user_id = ? OR contact_user_id = ?", id, id);
            
            jdbcTemplate.update("DELETE FROM health_alerts WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM health_vital_signs WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM medicine_box_slot WHERE user_medicine_id IN (SELECT id FROM user_medicines WHERE user_id = ?)", id);
            
            jdbcTemplate.update("DELETE FROM medicine_box WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM medicine_box_operation_log WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM medicine_operation_log WHERE user_id = ? OR operator_id = ?", id, id);
            
            jdbcTemplate.update("DELETE FROM medicine_reminder WHERE user_medicine_id IN (SELECT id FROM user_medicines WHERE user_id = ?)", id);
            
            jdbcTemplate.update("DELETE FROM medicine_stock_alert WHERE user_medicine_id IN (SELECT id FROM user_medicines WHERE user_id = ?)", id);
            
            jdbcTemplate.update("DELETE FROM medication_logs WHERE user_medicine_id IN (SELECT id FROM user_medicines WHERE user_id = ?)", id);
            
            jdbcTemplate.update("DELETE FROM user_medicines WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM medicine_draft WHERE elder_user_id = ? OR family_user_id = ?", id, id);
            
            jdbcTemplate.update("DELETE FROM mqtt_simulation_config WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM mqtt_simulation_history WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM news_like WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM notifications WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM user_community WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM user_devices WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM user_display_settings WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM user_health_config WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM user_interests WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM user_medical_mapping WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM user_profiles WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM friendships WHERE user_id = ? OR friend_id = ?", id, id);
            
            jdbcTemplate.update("DELETE FROM chat_records WHERE user_id = ?", id);
            
            jdbcTemplate.update("DELETE FROM messages WHERE from_id = ? OR to_id = ?", id, id);
            
            jdbcTemplate.update("DELETE FROM message_archive WHERE from_id = ? OR to_id = ? OR archived_by = ?", id, id, id);
            
            jdbcTemplate.update("DELETE FROM users WHERE id = ?", id);
            
            result.put("success", true);
            result.put("message", "删除成功，已清理所有关联数据");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("totalUsers", userManageService.getUserCount());
        result.put("elderCount", userManageService.getUserCountByRole("older"));
        result.put("familyCount", userManageService.getUserCountByRole("younger"));
        result.put("adminCount", userManageService.getUserCountByRole("admin"));
        return ResponseEntity.ok(result);
    }

    @GetMapping("/communities")
    public ResponseEntity<Map<String, Object>> getCommunities() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<Map<String, Object>> communityList = jdbcTemplate.queryForList(
                "SELECT id, community_name as communityName, admin_user_id, admin_phone FROM communities"
            );
            List<Map<String, Object>> neighborhoodList = jdbcTemplate.queryForList(
                "SELECT id, community_id, neighborhood_name as neighborhoodName FROM neighborhoods"
            );
            result.put("success", true);
            result.put("communities", communityList);
            result.put("neighborhoods", neighborhoodList);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "获取社区数据失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config/registration")
    public ResponseEntity<Map<String, Object>> getRegistrationStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("enabled", systemConfigService.isRegistrationEnabled());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/config/registration")
    public ResponseEntity<Map<String, Object>> setRegistrationStatus(@RequestBody Map<String, Boolean> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Boolean enabled = body.get("enabled");
            if (enabled == null) {
                result.put("success", false);
                result.put("message", "参数错误");
                return ResponseEntity.ok(result);
            }
            systemConfigService.setRegistrationEnabled(enabled);
            result.put("success", true);
            result.put("message", enabled ? "注册已开放" : "注册已关闭");
            result.put("enabled", enabled);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "设置失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config/{key}")
    public ResponseEntity<Map<String, Object>> getConfig(@PathVariable String key) {
        Map<String, Object> result = new HashMap<>();
        String value = systemConfigService.getConfigValue(key);
        result.put("success", true);
        result.put("key", key);
        result.put("value", value);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> setConfig(@RequestBody Map<String, String> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            String key = body.get("key");
            String value = body.get("value");
            String description = body.get("description");
            systemConfigService.setConfigValue(key, value, description);
            result.put("success", true);
            result.put("message", "配置保存成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config/tts")
    public ResponseEntity<Map<String, Object>> getTtsStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("enabled", systemConfigService.isTtsEnabled());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/config/tts")
    public ResponseEntity<Map<String, Object>> setTtsStatus(@RequestBody Map<String, Boolean> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Boolean enabled = body.get("enabled");
            if (enabled == null) {
                result.put("success", false);
                result.put("message", "参数错误");
                return ResponseEntity.ok(result);
            }
            systemConfigService.setTtsEnabled(enabled);
            result.put("success", true);
            result.put("message", enabled ? "语音合成已开启" : "语音合成已关闭");
            result.put("enabled", enabled);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "设置失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config/asr")
    public ResponseEntity<Map<String, Object>> getAsrStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("enabled", systemConfigService.isAsrEnabled());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/config/asr")
    public ResponseEntity<Map<String, Object>> setAsrStatus(@RequestBody Map<String, Boolean> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Boolean enabled = body.get("enabled");
            if (enabled == null) {
                result.put("success", false);
                result.put("message", "参数错误");
                return ResponseEntity.ok(result);
            }
            systemConfigService.setAsrEnabled(enabled);
            result.put("success", true);
            result.put("message", enabled ? "语音识别已开启" : "语音识别已关闭");
            result.put("enabled", enabled);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "设置失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/resources")
    public ResponseEntity<Map<String, Object>> getResourcesConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("config", systemConfigService.getResourcesConfig());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/resources")
    public ResponseEntity<Map<String, Object>> saveResourcesConfig(@RequestBody Map<String, String> config) {
        Map<String, Object> result = new HashMap<>();
        try {
            systemConfigService.saveResourcesConfig(config);
            result.put("success", true);
            result.put("message", "资源配置保存成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/config/crawler")
    public ResponseEntity<Map<String, Object>> getCrawlerStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("enabled", systemConfigService.isCrawlerEnabled());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/config/crawler")
    public ResponseEntity<Map<String, Object>> setCrawlerStatus(@RequestBody Map<String, Boolean> body) {
        Map<String, Object> result = new HashMap<>();
        try {
            Boolean enabled = body.get("enabled");
            if (enabled == null) {
                result.put("success", false);
                result.put("message", "参数错误");
                return ResponseEntity.ok(result);
            }
            systemConfigService.setCrawlerEnabled(enabled);
            result.put("success", true);
            result.put("message", enabled ? "爬虫已启用" : "爬虫已禁用");
            result.put("enabled", enabled);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "设置失败: " + e.getMessage());
        }
        return ResponseEntity.ok(result);
    }
}

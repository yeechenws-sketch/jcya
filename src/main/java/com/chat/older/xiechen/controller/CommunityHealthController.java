package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.*;
import com.chat.older.xiechen.model.*;
import com.chat.older.xiechen.handler.ChatHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import jakarta.servlet.http.HttpSession;
import java.util.*;

@RestController
@RequestMapping("/api/community/health")
public class CommunityHealthController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private UserHealthConfigMapper userHealthConfigMapper;

    @Autowired
    private HealthConfigLogMapper healthConfigLogMapper;

    @Autowired
    private HealthAdviceRecordMapper healthAdviceRecordMapper;

    @Autowired
    private HealthVitalSignsMapper healthVitalSignsMapper;

    @Autowired
    private EmergencyContactMapper emergencyContactMapper;

    @Autowired
    private ChatHandler chatHandler;

    @Autowired
    private MessageMapper messageMapper;

    @GetMapping("/elders/list")
    public Result<?> getEldersList(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("请先登录");
        }

        List<Map<String, Object>> elders = new ArrayList<>();
        List<User> allUsers = userMapper.selectAll();
        
        for (User user : allUsers) {
            if ("older".equals(user.getRole())) {
                Map<String, Object> elderInfo = new HashMap<>();
                elderInfo.put("id", user.getId());
                elderInfo.put("name", user.getNickname());
                elderInfo.put("avatar", user.getAvatar());
                
                UserProfile profile = userProfileMapper.selectById(user.getId());
                if (profile != null) {
                    elderInfo.put("phone", profile.getUserPhone());
                    elderInfo.put("address", profile.getAddressDetail());
                }
                
                UserHealthConfig config = userHealthConfigMapper.selectByUserId(user.getId());
                if (config != null) {
                    elderInfo.put("hrMin", config.getHrMin());
                    elderInfo.put("hrMax", config.getHrMax());
                    elderInfo.put("boMin", config.getBoMin());
                    elderInfo.put("bpSysMax", config.getBpSysMax());
                    elderInfo.put("bpDiaMax", config.getBpDiaMax());
                    elderInfo.put("bgFastingMax", config.getBgFastingMax());
                    elderInfo.put("configUpdateTime", config.getUpdateTime());
                    
                    Map<String, Object> riskAnalysis = analyzeConfigRisk(config);
                    elderInfo.put("riskLevel", riskAnalysis.get("riskLevel"));
                    elderInfo.put("riskWarnings", riskAnalysis.get("warnings"));
                }
                
                elders.add(elderInfo);
            }
        }
        
        return Result.success(elders);
    }

    @GetMapping("/elder/{userId}")
    public Result<?> getElderDetail(@PathVariable Long userId, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("请先登录");
        }

        User user = userMapper.getById(userId);
        if (user == null || !"older".equals(user.getRole())) {
            return Result.error("用户不存在或不是长辈用户");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("id", user.getId());
        result.put("name", user.getNickname());
        result.put("avatar", user.getAvatar());

        UserProfile profile = userProfileMapper.selectById(userId);
        if (profile != null) {
            result.put("phone", profile.getUserPhone());
            result.put("address", profile.getAddressDetail());
        }

        UserHealthConfig config = userHealthConfigMapper.selectByUserId(userId);
        result.put("healthConfig", config);

        if (config != null) {
            Map<String, Object> riskAnalysis = analyzeConfigRisk(config);
            result.put("riskAnalysis", riskAnalysis);
        }

        List<HealthConfigLog> logs = healthConfigLogMapper.selectByUserId(userId, 20);
        result.put("configLogs", logs);

        List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(userId);
        result.put("emergencyContacts", contacts);

        return Result.success(result);
    }

    @GetMapping("/logs")
    public Result<?> getConfigLogs(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int pageSize,
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String operatorType,
            HttpSession session) {
        
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("请先登录");
        }

        List<HealthConfigLog> logs;
        int total;
        
        if (userId != null) {
            logs = healthConfigLogMapper.selectByUserId(userId, pageSize);
            total = healthConfigLogMapper.countByUserId(userId);
        } else if (operatorType != null && !operatorType.isEmpty()) {
            logs = healthConfigLogMapper.selectByOperatorType(operatorType, pageSize);
            total = logs.size();
        } else {
            int offset = (page - 1) * pageSize;
            logs = healthConfigLogMapper.selectAll(pageSize, offset);
            total = healthConfigLogMapper.countAll();
        }

        Map<String, Object> result = new HashMap<>();
        result.put("list", logs);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", pageSize);
        
        return Result.success(result);
    }

    @GetMapping("/alerts")
    public Result<?> getHealthAlerts(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("请先登录");
        }

        List<Map<String, Object>> alerts = new ArrayList<>();
        List<User> allUsers = userMapper.selectAll();
        
        for (User user : allUsers) {
            if (!"older".equals(user.getRole())) continue;
            
            UserHealthConfig config = userHealthConfigMapper.selectByUserId(user.getId());
            if (config == null) continue;
            
            Map<String, Object> riskAnalysis = analyzeConfigRisk(config);
            String riskLevel = (String) riskAnalysis.get("riskLevel");
            
            if ("high".equals(riskLevel) || "critical".equals(riskLevel)) {
                Map<String, Object> alert = new HashMap<>();
                alert.put("userId", user.getId());
                alert.put("userName", user.getNickname());
                alert.put("riskLevel", riskLevel);
                alert.put("warnings", riskAnalysis.get("warnings"));
                alert.put("suggestions", riskAnalysis.get("suggestions"));
                
                UserProfile profile = userProfileMapper.selectById(user.getId());
                if (profile != null) {
                    alert.put("phone", profile.getUserPhone());
                }
                
                List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(user.getId());
                alert.put("emergencyContacts", contacts);
                
                alerts.add(alert);
            }
        }
        
        alerts.sort((a, b) -> {
            String levelA = (String) a.get("riskLevel");
            String levelB = (String) b.get("riskLevel");
            if ("critical".equals(levelA) && !"critical".equals(levelB)) return -1;
            if (!"critical".equals(levelA) && "critical".equals(levelB)) return 1;
            return 0;
        });
        
        return Result.success(alerts);
    }

    @PostMapping("/advice/send")
    public Result<?> sendHealthAdvice(
            @RequestBody Map<String, Object> params,
            HttpSession session) {
        
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("请先登录");
        }

        Long userId = Long.valueOf(params.get("userId").toString());
        String adviceType = (String) params.get("adviceType");
        String adviceContent = (String) params.get("adviceContent");
        String suggestedValues = params.get("suggestedValues") != null ? 
                params.get("suggestedValues").toString() : null;

        User elder = userMapper.getById(userId);
        if (elder == null) {
            return Result.error("用户不存在");
        }

        HealthAdviceRecord record = new HealthAdviceRecord();
        record.setUserId(userId);
        record.setUserName(elder.getNickname());
        record.setAdminId(currUser.getId());
        record.setAdminName(currUser.getNickname());
        record.setAdviceType(adviceType);
        record.setAdviceContent(adviceContent);
        record.setSuggestedValues(suggestedValues);
        record.setStatus(0);
        
        healthAdviceRecordMapper.insert(record);

        String messageContent = String.format("【健康建议】社区管理员 %s 向您发送了健康阈值建议：\n%s", 
                currUser.getNickname(), adviceContent);
        
        Message msg = new Message();
        msg.setFromId(currUser.getId());
        msg.setToId(userId);
        msg.setContent(messageContent);
        msg.setMsgType("health_advice");
        msg.setIsRead(0);
        msg.setCreateTime(new Date());
        messageMapper.insertMessage(msg);

        Map<String, Object> wsMsg = new HashMap<>();
        wsMsg.put("fromId", currUser.getId());
        wsMsg.put("toId", userId);
        wsMsg.put("content", messageContent);
        wsMsg.put("msgType", "health_advice");
        wsMsg.put("createTime", System.currentTimeMillis());
        chatHandler.sendToUser(userId, wsMsg);

        return Result.success("建议已发送");
    }

    @GetMapping("/advice/list")
    public Result<?> getAdviceList(
            @RequestParam(required = false) Long userId,
            HttpSession session) {
        
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("请先登录");
        }

        List<HealthAdviceRecord> records;
        if (userId != null) {
            records = healthAdviceRecordMapper.selectByUserId(userId);
        } else {
            records = healthAdviceRecordMapper.selectAll(100, 0);
        }
        
        return Result.success(records);
    }

    private Map<String, Object> analyzeConfigRisk(UserHealthConfig config) {
        Map<String, Object> result = new HashMap<>();
        List<String> warnings = new ArrayList<>();
        List<String> suggestions = new ArrayList<>();
        String riskLevel = "normal";

        if (config.getHrMin() != null && config.getHrMax() != null) {
            int hrRange = config.getHrMax() - config.getHrMin();
            if (hrRange > 80) {
                warnings.add("心率阈值范围过宽(" + config.getHrMin() + "-" + config.getHrMax() + "bpm)，可能遗漏异常");
                suggestions.add("建议心率范围：55-100bpm");
                riskLevel = "high";
            }
            if (config.getHrMin() < 40 || config.getHrMax() > 180) {
                warnings.add("心率阈值设置极端，存在严重安全隐患");
                riskLevel = "critical";
            }
        }

        if (config.getBoMin() != null) {
            if (config.getBoMin() < 85) {
                warnings.add("血氧下限过低(" + config.getBoMin() + "%)，危险阈值设置不当");
                suggestions.add("建议血氧下限：95%");
                riskLevel = "critical";
            } else if (config.getBoMin() < 90) {
                warnings.add("血氧下限偏低(" + config.getBoMin() + "%)");
                suggestions.add("建议血氧下限：95%");
                if (!"critical".equals(riskLevel)) {
                    riskLevel = "high";
                }
            }
        }

        if (config.getBpSysMax() != null) {
            if (config.getBpSysMax() > 200) {
                warnings.add("收缩压上限过高(" + config.getBpSysMax() + "mmHg)");
                suggestions.add("建议收缩压上限：140mmHg");
                if (!"critical".equals(riskLevel)) {
                    riskLevel = "high";
                }
            }
        }

        if (config.getBpDiaMax() != null) {
            if (config.getBpDiaMax() > 130) {
                warnings.add("舒张压上限过高(" + config.getBpDiaMax() + "mmHg)");
                suggestions.add("建议舒张压上限：90mmHg");
                if (!"critical".equals(riskLevel)) {
                    riskLevel = "high";
                }
            }
        }

        if (config.getBgFastingMax() != null) {
            if (config.getBgFastingMax() > 10) {
                warnings.add("空腹血糖上限过高(" + config.getBgFastingMax() + "mmol/L)");
                suggestions.add("建议空腹血糖上限：6.1mmol/L");
                if (!"critical".equals(riskLevel)) {
                    riskLevel = "high";
                }
            }
        }

        result.put("riskLevel", riskLevel);
        result.put("warnings", warnings);
        result.put("suggestions", suggestions);
        
        return result;
    }
}

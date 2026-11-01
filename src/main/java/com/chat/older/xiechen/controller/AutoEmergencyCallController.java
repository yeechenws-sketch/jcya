package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.service.AutoEmergencyCallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auto-emergency")
public class AutoEmergencyCallController {

    @Autowired
    private AutoEmergencyCallService autoEmergencyCallService;

    @PostMapping("/trigger")
    public Result triggerAutoEmergencyCall(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        String alertType = (String) params.get("alertType");
        Float value1 = params.get("value1") != null ? Float.valueOf(params.get("value1").toString()) : null;
        Float value2 = params.get("value2") != null ? Float.valueOf(params.get("value2").toString()) : null;
        Integer alertLevel = params.get("alertLevel") != null ? Integer.valueOf(params.get("alertLevel").toString()) : 1;

        Map<String, Object> result = autoEmergencyCallService.triggerAutoEmergencyCall(userId, alertType, value1, value2, alertLevel);
        
        if ((Boolean) result.get("success")) {
            return Result.success(result);
        } else {
            return Result.error((String) result.get("message"));
        }
    }

    @GetMapping("/status/{userId}")
    public Result getAutoCallStatus(@PathVariable Long userId) {
        Map<String, Object> status = autoEmergencyCallService.getAutoCallStatus(userId);
        return Result.success(status);
    }

    @PostMapping("/cancel/{userId}")
    public Result cancelAutoCall(@PathVariable Long userId) {
        autoEmergencyCallService.cancelAutoCall(userId);
        return Result.success("已取消自动呼叫");
    }

    @PostMapping("/answer")
    public Result answerAutoCall(@RequestBody Map<String, Object> params) {
        Long callRecordId = Long.valueOf(params.get("callRecordId").toString());
        Long answeredByUserId = Long.valueOf(params.get("answeredByUserId").toString());

        boolean success = autoEmergencyCallService.answerAutoCall(callRecordId, answeredByUserId);
        if (success) {
            return Result.success("接听成功");
        } else {
            return Result.error("接听失败，呼叫可能已处理或不存在");
        }
    }
}

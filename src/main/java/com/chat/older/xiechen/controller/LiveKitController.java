package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.service.LiveKitService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/livekit")
@RequiredArgsConstructor
public class LiveKitController {

    private final LiveKitService liveKitService;

    @PostMapping("/token")
    public Result<Map<String, String>> getToken(@RequestBody TokenRequest request) {
        try {
            String roomName = liveKitService.generateRoomName(
                request.getCallerId() != null ? request.getCallerId() : 0L,
                request.getCalleeId() != null ? request.getCalleeId() : 0L
            );
            String token = liveKitService.generateToken(
                String.valueOf(request.getUserId() != null ? request.getUserId() : 0L),
                request.getUserName() != null ? request.getUserName() : "用户",
                roomName
            );
            
            Map<String, String> result = new HashMap<>();
            result.put("token", token);
            result.put("roomName", roomName);
            result.put("wsUrl", liveKitService.getWsUrl());
            result.put("host", liveKitService.getHost());
            
            log.info("[LiveKit] Token请求成功 - 用户: {}, 房间: {}", request.getUserId(), roomName);
            return Result.success(result);
        } catch (Exception e) {
            log.error("[LiveKit] Token请求失败", e);
            return Result.error("获取Token失败: " + e.getMessage());
        }
    }

    @GetMapping("/config")
    public Result<Map<String, String>> getConfig() {
        Map<String, String> config = new HashMap<>();
        config.put("wsUrl", liveKitService.getWsUrl());
        config.put("host", liveKitService.getHost());
        return Result.success(config);
    }

    @Data
    public static class TokenRequest {
        private Long userId;
        private String userName;
        private Long callerId;
        private Long calleeId;
    }
}

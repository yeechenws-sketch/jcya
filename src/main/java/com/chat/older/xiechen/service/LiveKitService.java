package com.chat.older.xiechen.service;

import com.chat.older.xiechen.config.LiveKitConfig;
import io.livekit.server.AccessToken;
import io.livekit.server.RoomJoin;
import io.livekit.server.RoomName;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveKitService {

    private final LiveKitConfig liveKitConfig;

    public String generateToken(String userId, String userName, String roomName) {
        try {
            log.info("[LiveKit] 开始生成Token - apiKey: {}, apiSecret长度: {}", 
                liveKitConfig.getApiKey(), 
                liveKitConfig.getApiSecret() != null ? liveKitConfig.getApiSecret().length() : 0);
            
            AccessToken token = new AccessToken(liveKitConfig.getApiKey(), liveKitConfig.getApiSecret());
            
            token.setName(userName);
            token.setIdentity(userId);
            token.setMetadata(userName);
            
            token.addGrants(new RoomJoin(true), new RoomName(roomName));
            
            String jwt = token.toJwt();
            log.info("[LiveKit] 生成Token成功 - 用户: {}, 房间: {}, Token长度: {}", userId, roomName, jwt.length());
            return jwt;
        } catch (Exception e) {
            log.error("[LiveKit] 生成Token失败", e);
            throw new RuntimeException("生成LiveKit Token失败", e);
        }
    }

    public String generateRoomName(Long callerId, Long calleeId) {
        String id1 = String.valueOf(callerId);
        String id2 = String.valueOf(calleeId);
        
        if (id1.compareTo(id2) < 0) {
            return "call-" + id1 + "-" + id2;
        } else {
            return "call-" + id2 + "-" + id1;
        }
    }

    public String getWsUrl() {
        return liveKitConfig.getWsUrl();
    }

    public String getHost() {
        return liveKitConfig.getHost();
    }
}

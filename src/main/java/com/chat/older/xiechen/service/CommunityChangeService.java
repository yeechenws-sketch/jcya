package com.chat.older.xiechen.service;

public interface CommunityChangeService {
    
    void handleCommunityChange(Long userId, Integer oldCommunityId, Integer newCommunityId);
    
    void sendFriendRequestFromAdmin(Long adminId, Long userId);
    
    void sendWelcomeMessage(Long adminId, Long userId, Integer communityId);
    
    void sendFarewellMessage(Long adminId, Long userId, Integer communityId);
    
    void archiveChatMessages(Long userId, Long adminId, Integer communityId);
    
    void removeFriendship(Long userId, Long adminId);
}

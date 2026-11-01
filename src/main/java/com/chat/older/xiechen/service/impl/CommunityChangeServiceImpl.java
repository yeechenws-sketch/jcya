package com.chat.older.xiechen.service.impl;

import com.chat.older.xiechen.dao.*;
import com.chat.older.xiechen.handler.ChatHandler;
import com.chat.older.xiechen.model.*;
import com.chat.older.xiechen.service.CommunityChangeService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(rollbackFor = Exception.class)
public class CommunityChangeServiceImpl implements CommunityChangeService {

    private static final Logger logger = LoggerFactory.getLogger(CommunityChangeServiceImpl.class);

    private final UserMapper userMapper;
    private final CommunityMapper communityMapper;
    private final FriendMapper friendMapper;
    private final MessageMapper messageMapper;
    private final MessageArchiveMapper messageArchiveMapper;
    private final AutoMessageTemplateMapper autoMessageTemplateMapper;
    private final ChatHandler chatHandler;
    private final UserCommunityMapper userCommunityMapper;

    @Override
    public void handleCommunityChange(Long userId, Integer oldCommunityId, Integer newCommunityId) {
        logger.info("处理社区变更: userId={}, oldCommunityId={}, newCommunityId={}", userId, oldCommunityId, newCommunityId);

        if (oldCommunityId != null && !oldCommunityId.equals(newCommunityId)) {
            Community oldCommunity = communityMapper.selectById(oldCommunityId);
            if (oldCommunity != null && oldCommunity.getAdminUserId() != null) {
                Long oldAdminId = oldCommunity.getAdminUserId();

                sendFarewellMessage(oldAdminId, userId, oldCommunityId);

                archiveChatMessages(userId, oldAdminId, oldCommunityId);

                removeFriendship(userId, oldAdminId);
            }
        }

        Community newCommunity = communityMapper.selectById(newCommunityId);
        if (newCommunity != null && newCommunity.getAdminUserId() != null) {
            Long newAdminId = newCommunity.getAdminUserId();

            int existingRelation = friendMapper.countRelation(newAdminId, userId);
            if (existingRelation == 0) {
                sendFriendRequestFromAdmin(newAdminId, userId);
            } else {
                logger.info("好友关系已存在或申请中，跳过发送好友申请: adminId={}, userId={}", newAdminId, userId);
            }
        }
    }

    @Override
    public void sendFriendRequestFromAdmin(Long adminId, Long userId) {
        logger.info("管理员向用户发送好友申请: adminId={}, userId={}", adminId, userId);

        int existingRelation = friendMapper.countRelation(adminId, userId);
        if (existingRelation > 0) {
            logger.info("好友关系已存在或申请中，跳过: adminId={}, userId={}", adminId, userId);
            return;
        }

        User admin = userMapper.getById(adminId);
        User user = userMapper.getById(userId);
        
        if (admin == null || user == null) {
            logger.error("找不到管理员或用户信息: adminId={}, userId={}", adminId, userId);
            return;
        }

        friendMapper.saveRequest(adminId, userId, user.getNickname());
        logger.info("好友申请已保存: adminId={} -> userId={}", adminId, userId);

        Map<String, Object> notice = new HashMap<>();
        notice.put("fromId", adminId);
        notice.put("toId", userId);
        notice.put("msgType", "FRIEND_REQUEST");
        notice.put("content", admin.getNickname() + " 申请添加你为亲友");
        
        chatHandler.sendToUser(userId, notice);
        logger.info("好友申请通知已发送: userId={}", userId);
    }

    @Override
    public void sendWelcomeMessage(Long adminId, Long userId, Integer communityId) {
        logger.info("发送欢迎语: adminId={}, userId={}, communityId={}", adminId, userId, communityId);

        AutoMessageTemplate template = autoMessageTemplateMapper.findByScene("WELCOME");
        if (template == null) {
            logger.warn("未找到欢迎语模板");
            return;
        }

        Community community = communityMapper.selectById(communityId);
        if (community == null) {
            logger.error("未找到社区信息: communityId={}", communityId);
            return;
        }

        String content = template.getContent().replace("{community_name}", community.getCommunityName());
        
        Message message = new Message();
        message.setFromId(adminId);
        message.setToId(userId);
        message.setContent(content);
        message.setMsgType("text");
        message.setIsRead(0);
        
        messageMapper.insertMessage(message);
        logger.info("欢迎语已保存: from={}, to={}", adminId, userId);

        Map<String, Object> notice = new HashMap<>();
        notice.put("fromId", adminId);
        notice.put("toId", userId);
        notice.put("msgType", "TEXT");
        notice.put("content", content);
        
        chatHandler.sendToUser(userId, notice);
        logger.info("欢迎语已发送: userId={}", userId);
    }

    @Override
    public void sendFarewellMessage(Long adminId, Long userId, Integer communityId) {
        /*
                logger.info("发送告别语: adminId={}, userId={}, communityId={}", adminId, userId, communityId);

        AutoMessageTemplate template = autoMessageTemplateMapper.findByScene("FAREWELL");
        if (template == null) {
            logger.warn("未找到告别语模板");
            return;
        }

        Community community = communityMapper.selectById(communityId);
        if (community == null) {
            logger.error("未找到社区信息: communityId={}", communityId);
            return;
        }

        String content = template.getContent().replace("{community_name}", community.getCommunityName());

        Message message = new Message();
        message.setFromId(adminId);
        message.setToId(userId);
        message.setContent(content);
        message.setMsgType("text");
        message.setIsRead(1);

        messageMapper.insertMessage(message);


        logger.info("告别语已保存: from={}, to={}", adminId, userId);

        Map<String, Object> notice = new HashMap<>();
        notice.put("fromId", adminId);
        notice.put("toId", userId);
        notice.put("msgType", "TEXT");
        notice.put("content", content);

        chatHandler.sendToUser(userId, notice);
        logger.info("告别语已发送: userId={}", userId);
         */
    }

    @Override
    public void archiveChatMessages(Long userId, Long adminId, Integer communityId) {
        logger.info("归档聊天记录: userId={}, adminId={}", userId, adminId);

        List<Map<String, Object>> messages = messageMapper.getUnarchivedChatHistory(userId, adminId);
        
        if (messages == null || messages.isEmpty()) {
            logger.info("没有需要归档的聊天记录");
            return;
        }

        User user = userMapper.getById(userId);
        String username = user != null ? user.getUsername() : "unknown";

        for (Map<String, Object> msg : messages) {
            MessageArchive archive = new MessageArchive();
            archive.setOriginalMsgId(((Number) msg.get("id")).longValue());
            archive.setFromId(((Number) msg.get("fromId")).longValue());
            archive.setToId(((Number) msg.get("toId")).longValue());
            archive.setContent((String) msg.get("content"));
            archive.setMsgType((String) msg.get("msgType"));
            archive.setDuration(msg.get("duration") != null ? ((Number) msg.get("duration")).intValue() : 0);
            
            Object createTimeObj = msg.get("createTime");
            if (createTimeObj instanceof LocalDateTime) {
                archive.setCreateTime((LocalDateTime) createTimeObj);
            } else if (createTimeObj instanceof java.sql.Timestamp) {
                archive.setCreateTime(((java.sql.Timestamp) createTimeObj).toLocalDateTime());
            } else if (createTimeObj instanceof java.util.Date) {
                archive.setCreateTime(((java.util.Date) createTimeObj).toInstant()
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDateTime());
            }
            
            archive.setArchivedBy(userId);
            archive.setReason("搬离");
            archive.setCommunityId(communityId);
            
            messageArchiveMapper.insert(archive);
        }
        
        logger.info("聊天记录归档完成: 共{}条消息", messages.size());

        messageMapper.markAsArchived(userId, adminId);
        
        logger.info("消息已标记为已归档，聊天记录保留");
    }

    @Override
    public void removeFriendship(Long userId, Long adminId) {
        logger.info("解除好友关系: userId={}, adminId={}", userId, adminId);

        friendMapper.deleteFriend(userId, adminId);
        
        logger.info("好友关系已解除: userId={}, adminId={}", userId, adminId);

        Map<String, Object> notice = new HashMap<>();
        notice.put("fromId", adminId);
        notice.put("toId", userId);
        notice.put("msgType", "FRIEND_DELETE");
        
        chatHandler.sendToUser(userId, notice);
        chatHandler.sendToUser(adminId, notice);
        
        logger.info("好友解除通知已发送");
    }
}

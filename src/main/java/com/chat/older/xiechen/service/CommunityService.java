package com.chat.older.xiechen.service;

import com.chat.older.xiechen.dao.*;
import com.chat.older.xiechen.model.*;
import com.chat.older.xiechen.handler.ChatHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class CommunityService {

    @Autowired
    private CommunityMapper communityMapper;

    @Autowired
    private UserCommunityMapper userCommunityMapper;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private AutoMessageTemplateMapper autoMessageTemplateMapper;

    @Autowired
    private MessageArchiveMapper messageArchiveMapper;

    @Autowired
    private EmergencyContactMapper emergencyContactMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private ChatHandler chatHandler;

    private String getRealName(Long userId) {
        UserProfile profile = userProfileMapper.selectById(userId);
        return profile != null && profile.getRealName() != null ? profile.getRealName() : "未知";
    }

    @Transactional
    public void moveIn(Long userId, Integer communityId) {
        UserCommunity currentResidence = userCommunityMapper.findCurrentByUserId(userId);
        if (currentResidence != null && currentResidence.getStatus() == 1) {
            throw new RuntimeException("用户已在居住中，无法重复入住");
        }

        Community community = communityMapper.selectById(communityId);
        if (community == null) {
            throw new RuntimeException("社区不存在");
        }

        Long adminUserId = community.getAdminUserId();
        if (adminUserId == null) {
            throw new RuntimeException("该社区暂无负责人");
        }

        User user = userMapper.getById(userId);

        if (currentResidence != null) {
            userCommunityMapper.update(new UserCommunity() {{
                setId(currentResidence.getId());
                setStatus(0);
                setMoveOutTime(LocalDateTime.now());
            }});
        }

        UserCommunity newResidence = new UserCommunity();
        newResidence.setUserId(userId);
        newResidence.setCommunityId(communityId);
        newResidence.setStatus(1);
        newResidence.setMoveInTime(LocalDateTime.now());
        userCommunityMapper.insert(newResidence);

        if (friendMapper.countRelation(userId, adminUserId) == 0) {
            friendMapper.saveFriend(userId, adminUserId, community.getCommunityName() + " 负责人", 1);
            friendMapper.saveFriend(adminUserId, userId, "社区居民", 1);
        }

        String welcomeContent = String.format("【%s】新成员加入通知\\n\\n" +
                "您好，社区有新成员加入：\\n" +
                "• 姓名：%s\\n" +
                "• 昵称：%s\\n" +
                "• 加入时间：%s\\n\\n" +
                "请及时关注并提供必要的帮助。",
                community.getCommunityName(),
                getRealName(userId),
                user.getNickname(),
                LocalDateTime.now().toString().replace("T", " ").substring(0, 16));

        Message adminNotification = new Message();
        adminNotification.setFromId(0L);
        adminNotification.setToId(adminUserId);
        adminNotification.setContent(welcomeContent);
        adminNotification.setMsgType("system");
        adminNotification.setIsRead(0);
        messageMapper.insertMessage(adminNotification);

        Map<String, Object> adminNotice = new java.util.HashMap<>();
        adminNotice.put("fromId", 0L);
        adminNotice.put("toId", adminUserId);
        adminNotice.put("msgType", "COMMUNITY_JOIN_NOTICE");
        adminNotice.put("content", welcomeContent);
        adminNotice.put("fromName", "系统通知");
        chatHandler.sendToUser(adminUserId, adminNotice);

        AutoMessageTemplate template = autoMessageTemplateMapper.findByScene("WELCOME");
        if (template != null && template.getIsEnabled() == 1) {
            String simpleWelcome = template.getContent().replace("{community_name}", community.getCommunityName());
            Message msg = new Message();
            msg.setFromId(adminUserId);
            msg.setToId(userId);
            msg.setContent(simpleWelcome);
            msg.setMsgType("text");
            msg.setIsRead(0);
            messageMapper.insertMessage(msg);

            Map<String, Object> notice = new java.util.HashMap<>();
            notice.put("fromId", adminUserId);
            notice.put("toId", userId);
            notice.put("msgType", "WELCOME_MESSAGE");
            notice.put("content", simpleWelcome);
            chatHandler.sendToUser(userId, notice);

            String detailedWelcome = String.format("【%s】欢迎您的加入！\\n\\n" +
                    "亲爱的 %s，您好！\\n\\n" +
                    "感谢您加入【%s】大家庭！为了帮助您更好地了解和使用社区服务，请注意以下事项：\\n\\n" +
                    "【社区基本介绍】\\n" +
                    "• 社区名称：%s\\n" +
                    "• 社区负责人：%s\\n" +
                    "• 联系电话：%s\\n\\n" +
                    "【规则说明】\\n" +
                    "• 请遵守社区规章制度，共同维护和谐的社区环境\\n" +
                    "• 如有任何问题，可随时联系社区负责人\\n" +
                    "• 保持良好的邻里关系，互相帮助\\n\\n" +
                    "【常用功能指引】\\n" +
                    "• 紧急求助：遇紧急情况可点击右下角\"+\"按钮，选择\"紧急联系人\"快速求助\\n" +
                    "• 聊天功能：可与亲友、社区工作人员进行文字、语音沟通\\n" +
                    "• 健康档案：可在\"个人设置\"中完善健康信息\\n\\n" +
                    "再次欢迎您的加入！祝您生活愉快！",
                    community.getCommunityName(),
                    getRealName(userId),
                    community.getCommunityName(),
                    community.getCommunityName(),
                    community.getCommunityName() + " 负责人",
                    community.getAdminPhone() != null ? community.getAdminPhone() : "暂无");

            Message detailedMsg = new Message();
            detailedMsg.setFromId(0L);
            detailedMsg.setToId(userId);
            detailedMsg.setContent(detailedWelcome);
            detailedMsg.setMsgType("system");
            detailedMsg.setIsRead(0);
            messageMapper.insertMessage(detailedMsg);

            Map<String, Object> detailedNotice = new java.util.HashMap<>();
            detailedNotice.put("fromId", 0L);
            detailedNotice.put("toId", userId);
            detailedNotice.put("msgType", "COMMUNITY_WELCOME_DETAIL");
            detailedNotice.put("content", detailedWelcome);
            detailedNotice.put("fromName", "系统欢迎");
            chatHandler.sendToUser(userId, detailedNotice);
        }

        if (emergencyContactMapper.countRelation(userId, adminUserId) == 0) {
            emergencyContactMapper.saveSystemContact(userId, adminUserId, community.getCommunityName() + " 负责人", 1, "all");
        }
    }

    @Transactional
    public void moveOutAndMoveIn(Long userId, Integer newCommunityId) {
        UserCommunity currentResidence = userCommunityMapper.findCurrentByUserId(userId);
        if (currentResidence == null || currentResidence.getStatus() != 1) {
            throw new RuntimeException("用户当前未居住在任何社区，无法搬离");
        }

        Community oldCommunity = communityMapper.selectById(currentResidence.getCommunityId());
        if (oldCommunity == null) {
            throw new RuntimeException("原社区不存在");
        }

        List<Map<String, Object>> allChatHistory = messageMapper.getAllChatHistoryForArchive(userId);
        for (Map<String, Object> msg : allChatHistory) {
            MessageArchive archive = new MessageArchive();
            archive.setOriginalMsgId(msg.get("id") != null ? ((Number) msg.get("id")).longValue() : null);
            archive.setFromId(((Number) msg.get("from_id")).longValue());
            archive.setToId(((Number) msg.get("to_id")).longValue());
            archive.setContent((String) msg.get("content"));
            archive.setMsgType(msg.get("msg_type") != null ? (String) msg.get("msg_type") : "text");
            archive.setDuration(msg.get("duration") != null ? ((Number) msg.get("duration")).intValue() : 0);
            Object createTime = msg.get("create_time");
            if (createTime instanceof java.util.Date) {
                archive.setCreateTime(new java.sql.Timestamp(((java.util.Date) createTime).getTime()).toLocalDateTime());
            } else {
                archive.setCreateTime(LocalDateTime.now());
            }
            archive.setArchivedBy(userId);
            archive.setReason("MOVE_OUT");
            archive.setCommunityId(oldCommunity.getId());
            messageArchiveMapper.insert(archive);
        }

        messageMapper.physicalDeleteAllByUserId(userId);

        friendMapper.deleteAllByUserId(userId);

        currentResidence.setStatus(0);
        currentResidence.setMoveOutTime(LocalDateTime.now());
        userCommunityMapper.update(currentResidence);

        emergencyContactMapper.deleteAllSystemContacts(userId);

        moveIn(userId, newCommunityId);
    }

    public UserCommunity getCurrentResidence(Long userId) {
        return userCommunityMapper.findCurrentByUserId(userId);
    }
}

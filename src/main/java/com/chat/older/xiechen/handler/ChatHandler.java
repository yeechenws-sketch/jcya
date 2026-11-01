package com.chat.older.xiechen.handler;

import com.chat.older.xiechen.dao.FriendMapper;
import com.chat.older.xiechen.dao.MessageMapper;
import com.chat.older.xiechen.model.Message;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatHandler extends TextWebSocketHandler {

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private FriendMapper friendMapper;

    private static FriendMapper staticFriendMapper;

    @Autowired
    public void setStaticFriendMapper(FriendMapper friendMapper) {
        ChatHandler.staticFriendMapper = friendMapper;
    }

    private static final Map<Long, WebSocketSession> onlineUsers = new ConcurrentHashMap<>();

    private static final Map<Long, Long> currentChattingWith = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public static Set<Long> getOnlineUserIds() {
        return onlineUsers.keySet();
    }

    public static boolean isUserOnline(Long userId) {
        return onlineUsers.containsKey(userId);
    }

    private void broadcastOnlineStatus(Long userId, boolean isOnline) {
        if (staticFriendMapper == null) return;
        
        List<Map<String, Object>> friends = staticFriendMapper.getMyFriendsOrderByLastMessage(userId);
        if (friends == null) return;
        
        Map<String, Object> statusMsg = new HashMap<>();
        statusMsg.put("msgType", "USER_STATUS");
        statusMsg.put("userId", userId);
        statusMsg.put("online", isOnline);
        
        for (Map<String, Object> friend : friends) {
            Long friendId = ((Number) friend.get("id")).longValue();
            WebSocketSession friendSession = onlineUsers.get(friendId);
            if (friendSession != null && friendSession.isOpen()) {
                try {
                    friendSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(statusMsg)));
                } catch (Exception e) {
                    System.err.println("【广播在线状态失败】" + e.getMessage());
                }
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String path = session.getUri().getPath();
        Long userId = Long.parseLong(path.substring(path.lastIndexOf('/') + 1));

        onlineUsers.put(userId, session);
        currentChattingWith.put(userId, 0L);

        System.out.println("【WebSocket】用户上线: " + userId + "，当前总在线: " + onlineUsers.size());
        
        broadcastOnlineStatus(userId, true);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            String payload = message.getPayload();
            JsonNode jsonNode = objectMapper.readTree(payload);

            String msgType = jsonNode.has("type") ? jsonNode.get("type").asText() :
                    (jsonNode.has("msgType") ? jsonNode.get("msgType").asText() : "TEXT");

            if ("FOCUS_UPDATE".equals(msgType)) {
                Long myId = jsonNode.get("fromId").asLong();
                if (jsonNode.has("targetId") && !jsonNode.get("targetId").isNull()) {
                    currentChattingWith.put(myId, jsonNode.get("targetId").asLong());
                } else {
                    currentChattingWith.put(myId, 0L);
                }
                return;
            }

            if (msgType.startsWith("RTC_")) {
                Long fromId = jsonNode.get("fromId").asLong();
                Long toId = jsonNode.has("toId") ? jsonNode.get("toId").asLong() : 0L;


                if ("RTC_CALL_CANCEL".equals(msgType)) {
                    if (toId > 0) {
                        WebSocketSession target = onlineUsers.get(toId);
                        if (target != null && target.isOpen()) {
                            Map<String, Object> cancelMsg = new HashMap<>();
                            cancelMsg.put("type", "RTC_CALL_CANCEL");
                            cancelMsg.put("msgType", "RTC_CALL_CANCEL");
                            cancelMsg.put("fromId", fromId);
                            cancelMsg.put("toId", toId);
                            cancelMsg.put("data", new HashMap<>());

                            target.sendMessage(new TextMessage(objectMapper.writeValueAsString(cancelMsg)));
                            System.out.println("【WebSocket】发送取消通话信令: " + fromId + " -> " + toId);
                        }
                    }
                    return;
                }

                if (toId > 0) {
                    WebSocketSession target = onlineUsers.get(toId);
                    if (target != null && target.isOpen()) {
                        target.sendMessage(new TextMessage(payload));
                        System.out.println("【WebSocket】转发RTC信令: " + msgType + " to " + toId);
                    }
                }
                return;
            }

            Message msg = objectMapper.treeToValue(jsonNode, Message.class);
            
            System.out.println("【WebSocket】解析消息: fromId=" + msg.getFromId() + ", toId=" + msg.getToId() + ", content=" + msg.getContent() + ", msgType=" + msg.getMsgType());
            
            if (msg.getFromId() == null || msg.getToId() == null) {
                System.err.println("【WebSocket错误】fromId或toId为空: fromId=" + msg.getFromId() + ", toId=" + msg.getToId());
                return;
            }

            int isFriend = friendMapper.countRelation(msg.getFromId(), msg.getToId());
            if (isFriend <= 0) {
                System.out.println("【安全拦截】非好友尝试发送消息: " + msg.getFromId() + " -> " + msg.getToId());
                return;
            }

            Long targetViewingId = currentChattingWith.get(msg.getToId());
            if (targetViewingId != null && targetViewingId.equals(msg.getFromId())) {
                msg.setIsRead(1);
            } else {
                msg.setIsRead(0);
            }

            try {
                messageMapper.insertMessage(msg);
            } catch (Exception dbEx) {
                System.err.println("【数据库错误】插入消息失败: " + dbEx.getMessage());
                dbEx.printStackTrace();
                return;
            }

            WebSocketSession targetSession = onlineUsers.get(msg.getToId());
            if (targetSession != null && targetSession.isOpen()) {
                targetSession.sendMessage(new TextMessage(objectMapper.writeValueAsString(msg)));
            }

            System.out.println("【WebSocket】消息处理完成: " + msg.getFromId() + " -> " + msg.getToId() + " (已读:" + msg.getIsRead() + ")");

        } catch (Exception e) {
            System.err.println("【WebSocket严重错误】消息解析或处理失败: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        onlineUsers.entrySet().removeIf(entry -> {
            if (entry.getValue().equals(session)) {
                Long userId = entry.getKey();
                currentChattingWith.remove(userId);
                System.out.println("【WebSocket】用户下线: " + userId);
                broadcastOnlineStatus(userId, false);
                return true;
            }
            return false;
        });
    }

    public void sendToUser(Long toId, Map<String, Object> messageMap) {
        try {
            System.out.println("【WebSocket发送消息】目标用户ID: " + toId + ", 当前在线用户: " + onlineUsers.keySet());
            WebSocketSession session = onlineUsers.get(toId);
            if (session != null && session.isOpen()) {
                String message = objectMapper.writeValueAsString(messageMap);
                System.out.println("【WebSocket发送消息】发送到用户 " + toId + ": " + message);
                session.sendMessage(new TextMessage(message));
            } else {
                System.out.println("【WebSocket发送消息】用户 " + toId + " 不在线或会话已关闭");
            }
        } catch (Exception e) {
            System.err.println("【WebSocket系统通知异常】" + e.getMessage());
            e.printStackTrace();
        }
    }
}
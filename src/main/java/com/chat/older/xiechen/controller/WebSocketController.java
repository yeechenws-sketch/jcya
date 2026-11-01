package com.chat.older.xiechen.controller;

import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.server.PathParam;
import jakarta.websocket.server.ServerEndpoint;
import org.springframework.stereotype.Component;

import jakarta.websocket.Session;

import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

@ServerEndpoint("/ws/health/{userId}")
@Component
public class WebSocketController {

    private static final ConcurrentHashMap<Long, Session> userSessions = new ConcurrentHashMap<>();

    @OnOpen
    public void onOpen(Session session, @PathParam("userId") Long userId) {
        userSessions.put(userId, session);
        System.out.println("WebSocket连接建立: 用户 " + userId);
    }

    @OnClose
    public void onClose(Session session, @PathParam("userId") Long userId) {
        userSessions.remove(userId);
        System.out.println("WebSocket连接关闭: 用户 " + userId);
    }

    @OnMessage
    public void onMessage(String message, @PathParam("userId") Long userId) {
        System.out.println("收到用户 " + userId + " 的消息: " + message);
    }

    @OnError
    public void onError(Session session, Throwable error, @PathParam("userId") Long userId) {
        System.err.println("WebSocket错误: 用户 " + userId + ", 错误: " + error.getMessage());
        error.printStackTrace();
    }

    public static void sendMessageToUser(Long userId, String message) {
        Session session = userSessions.get(userId);
        if (session != null && session.isOpen()) {
            try {
                session.getBasicRemote().sendText(message);
            } catch (IOException e) {
                System.err.println("发送消息失败: " + e.getMessage());
            }
        }
    }

    public static void sendMessageToAll(String message) {
        for (Session session : userSessions.values()) {
            if (session.isOpen()) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    System.err.println("发送消息失败: " + e.getMessage());
                }
            }
        }
    }
}

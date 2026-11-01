package com.chat.older.xiechen.service.impl;

import com.chat.older.xiechen.dao.*;
import com.chat.older.xiechen.handler.ChatHandler;
import com.chat.older.xiechen.model.*;
import com.chat.older.xiechen.service.AutoEmergencyCallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class AutoEmergencyCallServiceImpl implements AutoEmergencyCallService {

    @Autowired
    private EmergencyContactMapper emergencyContactMapper;

    @Autowired
    private EmergencyCallRecordMapper emergencyCallRecordMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ChatHandler chatHandler;

    private final ExecutorService executorService = Executors.newCachedThreadPool();
    private final Map<Long, AutoCallContext> autoCallContexts = new ConcurrentHashMap<>();

    private static class AutoCallContext {
        Long userId;
        String alertType;
        Float value1;
        Float value2;
        Integer alertLevel;
        EmergencyCallRecord callRecord;
        boolean isAnswered = false;
        boolean isCancelled = false;
        List<Map<String, Object>> contacts;
        Long answeredContactUserId;
        String answeredContactName;
    }

    @Override
    public Map<String, Object> triggerAutoEmergencyCall(Long userId, String alertType, Float value1, Float value2, Integer alertLevel) {
        Map<String, Object> result = new HashMap<>();

        User user = userMapper.getById(userId);
        if (user == null) {
            result.put("success", false);
            result.put("message", "用户不存在");
            return result;
        }

        List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(userId);
        if (contacts == null || contacts.isEmpty()) {
            result.put("success", false);
            result.put("message", "未设置紧急联系人");
            return result;
        }

        List<Map<String, Object>> contactList = contacts.stream()
                .filter(c -> c.getIsStaff() == 0)
                .map(c -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", c.getId());
                    map.put("contactUserId", c.getContactUserId());
                    map.put("contactName", c.getContactName());
                    map.put("contactPhone", c.getContactPhone());

                    User contactUser = userMapper.getById(c.getContactUserId());
                    if (contactUser != null) {
                        map.put("nickname", contactUser.getNickname());
                        map.put("avatar", contactUser.getAvatar());
                    }
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());

        if (contactList.isEmpty()) {
            result.put("success", false);
            result.put("message", "没有可呼叫的紧急联系人");
            return result;
        }

        EmergencyCallRecord record = new EmergencyCallRecord();
        record.setElderUserId(userId);
        record.setElderUserName(user.getNickname());
        record.setCallType("auto");
        record.setAlertType(alertType);
        record.setAlertLevel(alertLevel);
        record.setAlertValue(value1 != null ? String.valueOf(value1) : (value2 != null ? String.valueOf(value2) : ""));
        record.setStartTime(LocalDateTime.now());
        record.setStatus("calling");
        record.setNotificationStatus("pending");
        emergencyCallRecordMapper.insert(record);

        AutoCallContext context = new AutoCallContext();
        context.userId = userId;
        context.alertType = alertType;
        context.value1 = value1;
        context.value2 = value2;
        context.alertLevel = alertLevel;
        context.callRecord = record;
        context.contacts = contactList;
        autoCallContexts.put(userId, context);

        executorService.submit(() -> performAutoCall(context));

        result.put("success", true);
        result.put("message", "自动紧急呼叫已触发");
        result.put("callRecordId", record.getId());
        result.put("contacts", contactList);
        result.put("alertType", alertType);
        result.put("alertValue", value1 != null ? value1 : value2);
        result.put("alertLevel", alertLevel);

        System.out.println("【自动紧急呼叫】已触发 - 用户: " + user.getNickname() + 
            ", 类型: " + alertType + ", 值: " + (value1 != null ? value1 : value2) + 
            ", 级别: " + alertLevel);

        return result;
    }

    private void performAutoCall(AutoCallContext context) {
        try {
            Thread.sleep(3000);

            if (context.isCancelled) {
                context.callRecord.setStatus("cancelled");
                context.callRecord.setEndTime(LocalDateTime.now());
                emergencyCallRecordMapper.update(context.callRecord);
                autoCallContexts.remove(context.userId);
                return;
            }

            for (Map<String, Object> contact : context.contacts) {
                if (context.isAnswered || context.isCancelled) {
                    break;
                }

                Long contactUserId = (Long) contact.get("contactUserId");
                String contactName = (String) contact.get("contactName");

                if (contactUserId == null) continue;

                sendCallNotification(context, contact);

                // 移除自动模拟接听逻辑，等待真实的联系人通过 WebSocket 接听
                // simulateContactResponse(context, contact);
            }

            // 等待 30 秒，如果没有人接听，则标记为无人接听
            if (!context.isAnswered && !context.isCancelled) {
                Thread.sleep(30000);
                
                if (!context.isAnswered && !context.isCancelled) {
                    context.callRecord.setStatus("no_answer");
                    context.callRecord.setEndTime(LocalDateTime.now());
                    emergencyCallRecordMapper.update(context.callRecord);

                    sendNotificationToAllContacts(context, null);
                }
            }

        } catch (Exception e) {
            System.err.println("【自动紧急呼叫】执行异常: " + e.getMessage());
            context.callRecord.setStatus("error");
            context.callRecord.setEndTime(LocalDateTime.now());
            emergencyCallRecordMapper.update(context.callRecord);
        } finally {
            autoCallContexts.remove(context.userId);
        }
    }

    private void sendCallNotification(AutoCallContext context, Map<String, Object> contact) {
        Long contactUserId = (Long) contact.get("contactUserId");
        String contactName = (String) contact.get("contactName");

        String alertTypeName = getAlertTypeName(context.alertType);
        String valueStr = context.value1 != null ? String.valueOf(context.value1) : 
                         (context.value2 != null ? String.valueOf(context.value2) : "");

        String content = String.format("紧急呼叫：自动呼叫，%s预警，%s值为：%s，请立即接听！",
            alertTypeName, alertTypeName, valueStr);

        try {
            Message message = new Message();
            message.setFromId(context.userId);
            message.setToId(contactUserId);
            message.setContent(content);
            message.setMsgType("text");
            message.setCreateTime(new Date());
            message.setIsRead(0);
            message.setDuration(0);
            messageMapper.insertMessage(message);

            Map<String, Object> notice = new HashMap<>();
            notice.put("msgType", "EMERGENCY_CALL_NOTIFICATION");
            notice.put("callRecordId", context.callRecord.getId());
            notice.put("content", content);
            notice.put("alertType", context.alertType);
            notice.put("alertLevel", context.alertLevel);
            notice.put("alertValue", context.value1 != null ? context.value1 : context.value2);
            notice.put("timestamp", System.currentTimeMillis());
            chatHandler.sendToUser(contactUserId, notice);

            System.out.println("【自动紧急呼叫】已向 " + contactName + " 发送呼叫通知");
        } catch (Exception e) {
            System.err.println("【自动紧急呼叫】发送通知失败: " + e.getMessage());
        }
    }

    private void simulateContactResponse(AutoCallContext context, Map<String, Object> contact) {
        try {
            Thread.sleep(2000);

            if (context.isCancelled) return;

            Random random = new Random();
            boolean isAnswered = random.nextInt(100) < 40;

            if (isAnswered) {
                context.isAnswered = true;
                context.answeredContactUserId = (Long) contact.get("contactUserId");
                context.answeredContactName = (String) contact.get("contactName");

                context.callRecord.setAnsweredByUserId(context.answeredContactUserId);
                context.callRecord.setAnsweredByUserName(context.answeredContactName);
                context.callRecord.setStatus("answered");
                context.callRecord.setEndTime(LocalDateTime.now());
                emergencyCallRecordMapper.update(context.callRecord);

                System.out.println("【自动紧急呼叫】联系人 " + context.answeredContactName + " 已接听");

                sendNotificationToAllContacts(context, context.answeredContactName);
            }
        } catch (Exception e) {
            System.err.println("【自动紧急呼叫】模拟接听异常: " + e.getMessage());
        }
    }

    private void sendNotificationToAllContacts(AutoCallContext context, String answeredName) {
        String alertTypeName = getAlertTypeName(context.alertType);
        String valueStr = context.value1 != null ? String.valueOf(context.value1) : 
                         (context.value2 != null ? String.valueOf(context.value2) : "");

        String content;
        if (answeredName != null && !answeredName.isEmpty()) {
            content = String.format("紧急呼叫：自动呼叫，%s预警，%s值为：%s，%s已接通",
                alertTypeName, alertTypeName, valueStr, answeredName);
        } else {
            content = String.format("紧急呼叫：自动呼叫，%s预警，%s值为：%s，无人接听",
                alertTypeName, alertTypeName, valueStr);
        }

        for (Map<String, Object> contact : context.contacts) {
            Long contactUserId = (Long) contact.get("contactUserId");
            String contactPhone = (String) contact.get("contactPhone");
            String contactName = (String) contact.get("contactName");

            try {
                if (contactUserId != null) {
                    Message message = new Message();
                    message.setFromId(context.userId);
                    message.setToId(contactUserId);
                    message.setContent(content);
                    message.setMsgType("text");
                    message.setCreateTime(new Date());
                    message.setIsRead(0);
                    message.setDuration(0);
                    messageMapper.insertMessage(message);

                    Map<String, Object> notice = new HashMap<>();
                    notice.put("msgType", "EMERGENCY_NOTIFICATION");
                    notice.put("callRecordId", context.callRecord.getId());
                    notice.put("content", content);
                    notice.put("alertType", context.alertType);
                    notice.put("alertLevel", context.alertLevel);
                    notice.put("answeredName", answeredName);
                    notice.put("timestamp", System.currentTimeMillis());
                    chatHandler.sendToUser(contactUserId, notice);
                }

                if (contactPhone != null && !contactPhone.isEmpty()) {
                    sendSmsNotification(contactPhone, content);
                }

                System.out.println("【自动紧急呼叫】已向 " + contactName + " 发送结果通知: " + content);

            } catch (Exception e) {
                System.err.println("【自动紧急呼叫】发送通知失败: " + e.getMessage());
            }
        }

        context.callRecord.setNotificationStatus("sent");
        emergencyCallRecordMapper.update(context.callRecord);
    }

    private void sendSmsNotification(String phone, String content) {
        System.out.println("【自动紧急呼叫】模拟短信发送: " + phone + ", 内容: " + content);
    }

    private String getAlertTypeName(String alertType) {
        switch (alertType) {
            case "BP": return "血压";
            case "HR": return "心率";
            case "BO": return "血氧";
            case "BG": return "血糖";
            default: return alertType;
        }
    }

    @Override
    public Map<String, Object> getAutoCallStatus(Long userId) {
        Map<String, Object> result = new HashMap<>();
        AutoCallContext context = autoCallContexts.get(userId);

        if (context != null) {
            result.put("isRunning", true);
            result.put("callRecordId", context.callRecord.getId());
            result.put("alertType", context.alertType);
            result.put("alertValue", context.value1 != null ? context.value1 : context.value2);
            result.put("alertLevel", context.alertLevel);
            result.put("isAnswered", context.isAnswered);
            result.put("answeredContactName", context.answeredContactName);
        } else {
            result.put("isRunning", false);
        }

        return result;
    }

    @Override
    public void cancelAutoCall(Long userId) {
        AutoCallContext context = autoCallContexts.get(userId);
        if (context != null) {
            context.isCancelled = true;
            
            if (context.callRecord != null) {
                context.callRecord.setStatus("cancelled");
                context.callRecord.setEndTime(LocalDateTime.now());
                context.callRecord.setAnsweredByUserId(null);
                context.callRecord.setAnsweredByUserName(null);
                emergencyCallRecordMapper.update(context.callRecord);
            }
            
            System.out.println("【自动紧急呼叫】用户 " + userId + " 已取消自动呼叫");
        }
    }

    @Override
    public boolean answerAutoCall(Long callRecordId, Long answeredByUserId) {
        EmergencyCallRecord record = emergencyCallRecordMapper.selectById(callRecordId);
        if (record == null) {
            System.out.println("【自动紧急呼叫】找不到呼叫记录: " + callRecordId);
            return false;
        }

        if (!"calling".equals(record.getStatus())) {
            System.out.println("【自动紧急呼叫】呼叫已处理，当前状态: " + record.getStatus());
            return false;
        }

        Long elderUserId = record.getElderUserId();
        AutoCallContext context = autoCallContexts.get(elderUserId);

        User answerer = userMapper.getById(answeredByUserId);
        String answererName = answerer != null ? answerer.getNickname() : "联系人";

        record.setAnsweredByUserId(answeredByUserId);
        record.setAnsweredByUserName(answererName);
        record.setStatus("answered");
        record.setEndTime(LocalDateTime.now());
        emergencyCallRecordMapper.update(record);

        if (context != null) {
            context.isAnswered = true;
            context.answeredContactUserId = answeredByUserId;
            context.answeredContactName = answererName;
            System.out.println("【自动紧急呼叫】" + answererName + " 已接听，内存状态已更新");
        }

        sendNotificationToAllContacts(context != null ? context : createContextFromRecord(record), answererName);

        if (context != null) {
            autoCallContexts.remove(elderUserId);
        }

        return true;
    }

    private AutoCallContext createContextFromRecord(EmergencyCallRecord record) {
        AutoCallContext context = new AutoCallContext();
        context.userId = record.getElderUserId();
        context.alertType = record.getAlertType();
        context.callRecord = record;
        context.isAnswered = true;
        context.answeredContactName = record.getAnsweredByUserName();

        List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(record.getElderUserId());
        context.contacts = contacts.stream()
                .filter(c -> c.getIsStaff() == 0)
                .map(c -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", c.getId());
                    map.put("contactUserId", c.getContactUserId());
                    map.put("contactName", c.getContactName());
                    map.put("contactPhone", c.getContactPhone());
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());

        return context;
    }
}

package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.*;
import com.chat.older.xiechen.handler.ChatHandler;
import com.chat.older.xiechen.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.*;

@RestController
@RequestMapping("/api/emergency")
public class EmergencyCallController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private EmergencyContactMapper emergencyContactMapper;

    @Autowired
    private EmergencyCallRecordMapper emergencyCallRecordMapper;

    @Autowired
    private MessageMapper messageMapper;

    @Autowired
    private ChatHandler chatHandler;

    @PostMapping("/call/start")
    public Result startEmergencyCall(HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        System.out.println("【紧急呼叫】" + me.getNickname() + " 发起紧急呼叫");

        List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(me.getId());
        if (contacts == null || contacts.isEmpty()) {
            return Result.error("未设置紧急联系人");
        }

        EmergencyCallRecord record = new EmergencyCallRecord();
        record.setElderUserId(me.getId());
        record.setElderUserName(me.getNickname());
        record.setCallType("manual");
        record.setStartTime(LocalDateTime.now());
        record.setStatus("calling");
        record.setNotificationStatus("pending");
        emergencyCallRecordMapper.insert(record);

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
                        map.put("name", contactUser.getNickname());
                    }
                    return map;
                })
                .collect(java.util.stream.Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("callRecordId", record.getId());
        result.put("contacts", contactList);
        result.put("elderName", me.getNickname());

        return Result.success(result);
    }

    @PostMapping("/call/answered")
    public Result emergencyCallAnswered(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        Long callRecordId = Long.valueOf(params.get("callRecordId").toString());
        Long answeredByUserId = Long.valueOf(params.get("answeredByUserId").toString());

        System.out.println("【紧急呼叫】接听记录: " + callRecordId + ", 接听人: " + answeredByUserId);

        EmergencyCallRecord record = emergencyCallRecordMapper.selectById(callRecordId);
        if (record == null) {
            return Result.error("找不到紧急呼叫记录");
        }

        if (!"calling".equals(record.getStatus())) {
            return Result.error("该呼叫已处理");
        }

        User answerer = userMapper.getById(answeredByUserId);
        String answererName = answerer != null ? answerer.getNickname() : "联系人";

        record.setAnsweredByUserId(answeredByUserId);
        record.setAnsweredByUserName(answererName);
        record.setStatus("answered");
        record.setEndTime(LocalDateTime.now());
        emergencyCallRecordMapper.update(record);

        sendEmergencyNotifications(record);

        return Result.success("接听成功");
    }

    @PostMapping("/call/cancel")
    public Result cancelEmergencyCall(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        Long callRecordId = Long.valueOf(params.get("callRecordId").toString());

        System.out.println("【紧急呼叫】取消呼叫: " + callRecordId);

        EmergencyCallRecord record = emergencyCallRecordMapper.selectById(callRecordId);
        if (record == null) {
            return Result.error("找不到紧急呼叫记录");
        }

        record.setStatus("cancelled");
        record.setEndTime(LocalDateTime.now());
        record.setAnsweredByUserId(null);
        record.setAnsweredByUserName(null);
        emergencyCallRecordMapper.update(record);

        System.out.println("【紧急呼叫】已取消呼叫：" + callRecordId + "，状态更新为 cancelled");

        return Result.success("已取消");
    }

    @GetMapping("/call/history")
    public Result getEmergencyCallHistory(HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        List<EmergencyCallRecord> records = emergencyCallRecordMapper.selectByElderId(me.getId());
        return Result.success(records);
    }



    private void sendEmergencyNotifications(EmergencyCallRecord record) {
        System.out.println("【紧急呼叫】开始发送通知...");

        String notificationContent = String.format(
            "紧急呼叫：手动呼叫 %s已接通", 
            record.getAnsweredByUserName()
        );

        List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(record.getElderUserId());

        for (EmergencyContact contact : contacts) {
            try {
                if (contact.getContactUserId() != null) {
                    sendAppMessage(record.getElderUserId(), contact.getContactUserId(), notificationContent);
                }

                if (contact.getContactPhone() != null && !contact.getContactPhone().isEmpty()) {
                    sendSmsNotification(contact.getContactPhone(), notificationContent);
                }

            } catch (Exception e) {
                System.err.println("【紧急呼叫】发送通知失败: " + e.getMessage());
            }
        }

        record.setNotificationStatus("sent");
        emergencyCallRecordMapper.update(record);
        System.out.println("【紧急呼叫】通知发送完成");
    }

    private void sendAppMessage(Long fromUserId, Long toUserId, String content) {
        try {
            Message message = new Message();
            message.setFromId(fromUserId);
            message.setToId(toUserId);
            message.setContent(content);
            message.setMsgType("text");
            message.setCreateTime(new Date());
            message.setIsRead(0);
            message.setDuration(0);
            messageMapper.insertMessage(message);

            Map<String, Object> notice = new HashMap<>();
            notice.put("msgType", "EMERGENCY_NOTIFICATION");
            notice.put("content", content);
            notice.put("timestamp", System.currentTimeMillis());
            chatHandler.sendToUser(toUserId, notice);

            System.out.println("【紧急呼叫】应用内消息已发送给用户: " + toUserId);
        } catch (Exception e) {
            System.err.println("【紧急呼叫】发送应用内消息失败: " + e.getMessage());
        }
    }

    private void sendSmsNotification(String phone, String content) {
        System.out.println("【紧急呼叫】模拟短信发送: " + phone + ", 内容: " + content);
    }
}

package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.EmergencyContactMapper;
import com.chat.older.xiechen.dao.FriendMapper;
import com.chat.older.xiechen.dao.UserMapper;
import com.chat.older.xiechen.dao.UserProfileMapper;
import com.chat.older.xiechen.handler.ChatHandler;
import com.chat.older.xiechen.model.EmergencyContact;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.model.UserProfile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/older")
public class OlderEmergencyController {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private EmergencyContactMapper emergencyContactMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    @Autowired
    private ChatHandler chatHandler;

    @GetMapping("/family/list")
    public Result getFamilyList(HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        
        List<Map<String, Object>> family = friends.stream()
                .filter(f -> "young".equals(f.get("role")))
                .map(f -> {
                    Map<String, Object> member = new HashMap<>(f);
                    Long memberId = ((Number) f.get("id")).longValue();
                    
                    boolean isEmergency = emergencyContactMapper.countRelation(me.getId(), memberId) > 0;
                    member.put("isEmergency", isEmergency);
                    
                    UserProfile profile = userProfileMapper.selectById(memberId);
                    if (profile != null) {
                        member.put("phone", profile.getUserPhone());
                    }
                    return member;
                })
                .collect(Collectors.toList());

        return Result.success(family);
    }

    @PostMapping("/emergency/set")
    public Result setEmergencyContact(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        Long contactUserId = Long.valueOf(params.get("contactUserId").toString());
        String contactName = params.get("contactName") != null ? params.get("contactName").toString() : "";
        String contactPhone = params.get("contactPhone") != null ? params.get("contactPhone").toString() : "";

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        boolean isRelated = friends.stream().anyMatch(f -> {
            Object id = f.get("id");
            Long friendId = null;
            if (id instanceof Number) {
                friendId = ((Number) id).longValue();
            }
            return contactUserId.equals(friendId);
        });

        if (!isRelated) {
            return Result.error("该用户不是您的亲友");
        }

        if (emergencyContactMapper.countRelation(me.getId(), contactUserId) > 0) {
            EmergencyContact existing = emergencyContactMapper.selectByUserIdAndContactUserId(me.getId(), contactUserId);
            existing.setContactName(contactName);
            existing.setContactPhone(contactPhone);
            existing.setUpdateTime(LocalDateTime.now());
            emergencyContactMapper.update(existing);
            
            sendNotification(contactUserId, me.getNickname(), true);
            return Result.success("更新成功");
        }

        EmergencyContact contact = new EmergencyContact();
        contact.setUserId(me.getId());
        contact.setContactUserId(contactUserId);
        contact.setContactName(contactName);
        contact.setContactPhone(contactPhone);
        contact.setIsStaff(0);
        contact.setIsSystemAdd(0);
        contact.setPriority(1);
        contact.setUpdateTime(LocalDateTime.now());

        emergencyContactMapper.insert(contact);
        
        sendNotification(contactUserId, me.getNickname(), true);

        return Result.success("设置成功");
    }

    @PostMapping("/emergency/remove")
    public Result removeEmergencyContact(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        Long contactUserId = Long.valueOf(params.get("contactUserId").toString());

        EmergencyContact contact = emergencyContactMapper.selectByUserIdAndContactUserId(me.getId(), contactUserId);
        if (contact != null && contact.getIsSystemAdd() == 0) {
            emergencyContactMapper.deleteById(contact.getId());
            
            sendNotification(contactUserId, me.getNickname(), false);
        }

        return Result.success("已移除");
    }

    private void sendNotification(Long toUserId, String elderName, boolean isAdd) {
        Map<String, Object> notice = new HashMap<>();
        if (isAdd) {
            notice.put("msgType", "BECOME_EMERGENCY_CONTACT");
            notice.put("elderName", elderName);
            notice.put("message", "你已成为" + elderName + "的紧急联系人");
        } else {
            notice.put("msgType", "REMOVED_EMERGENCY_CONTACT");
            notice.put("elderName", elderName);
            notice.put("message", "你已被移除" + elderName + "的紧急联系人");
        }
        chatHandler.sendToUser(toUserId, notice);
    }
}

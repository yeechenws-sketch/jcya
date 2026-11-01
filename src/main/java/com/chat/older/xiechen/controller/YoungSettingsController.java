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
@RequestMapping("/api")
public class YoungSettingsController {

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

    @GetMapping("/user/search")
    public Result searchUser(@RequestParam String username, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        User user = userMapper.findByUsername(username);
        if (user == null) {
            return Result.error("未找到该用户");
        }

        if (user.getId().equals(me.getId())) {
            return Result.error("不能添加自己");
        }

        Map<String, Object> data = new HashMap<>();
        data.put("id", user.getId());
        data.put("username", user.getUsername());
        data.put("nickname", user.getNickname());
        data.put("avatar", user.getAvatar());
        data.put("role", user.getRole());

        return Result.success(data);
    }

    @PostMapping("/emergency/add")
    public Result addEmergencyContact(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        Long contactUserId = Long.valueOf(params.get("contactUserId").toString());
        String contactName = params.get("contactName") != null ? params.get("contactName").toString() : "";
        String contactPhone = params.get("contactPhone") != null ? params.get("contactPhone").toString() : "";

        if (emergencyContactMapper.countRelation(me.getId(), contactUserId) > 0) {
            EmergencyContact existing = emergencyContactMapper.selectByUserIdAndContactUserId(me.getId(), contactUserId);
            existing.setContactName(contactName);
            existing.setContactPhone(contactPhone);
            existing.setUpdateTime(LocalDateTime.now());
            emergencyContactMapper.update(existing);
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
        return Result.success("添加成功");
    }

    @PostMapping("/emergency/remove")
    public Result removeEmergencyContact(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        Long contactUserId = Long.valueOf(params.get("contactUserId").toString());
        
        EmergencyContact contact = emergencyContactMapper.selectByUserIdAndContactUserId(me.getId(), contactUserId);
        if (contact != null && contact.getIsSystemAdd() == 0) {
            emergencyContactMapper.deleteById(contact.getId());
        }

        return Result.success("移除成功");
    }

    @GetMapping("/emergency/list")
    public Result getEmergencyContacts(HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(me.getId());
        List<Map<String, Object>> result = contacts.stream()
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
                .collect(Collectors.toList());

        return Result.success(result);
    }

    @GetMapping("/elders/with-emergency")
    public Result getEldersWithEmergency(HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        List<EmergencyContact> emergencyContacts = emergencyContactMapper.selectByUserId(me.getId());
        
        Map<Long, Boolean> emergencyMap = emergencyContacts.stream()
                .filter(c -> c.getIsStaff() == 0)
                .collect(Collectors.toMap(
                        EmergencyContact::getContactUserId,
                        c -> true,
                        (a, b) -> a
                ));

        List<Map<String, Object>> elders = friends.stream()
                .filter(f -> "older".equals(f.get("role")))
                .map(f -> {
                    Map<String, Object> elder = new HashMap<>(f);
                    Long friendId = ((Number) f.get("id")).longValue();
                    elder.put("isEmergency", emergencyMap.getOrDefault(friendId, false));
                    
                    UserProfile profile = userProfileMapper.selectById(friendId);
                    if (profile != null) {
                        elder.put("phone", profile.getUserPhone());
                    }
                    return elder;
                })
                .collect(Collectors.toList());

        return Result.success(elders);
    }

    @GetMapping("/emergency/elder/{elderId}")
    public Result getElderEmergencyContacts(@PathVariable Long elderId, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        boolean isRelated = friends.stream().anyMatch(f -> {
            Object id = f.get("id");
            Long friendId = null;
            if (id instanceof Number) {
                friendId = ((Number) id).longValue();
            }
            return elderId.equals(friendId);
        });

        if (!isRelated) {
            return Result.error("您没有权限查看该长辈的信息");
        }

        List<EmergencyContact> contacts = emergencyContactMapper.selectByUserId(elderId);
        List<Map<String, Object>> result = contacts.stream()
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
                .collect(Collectors.toList());

        return Result.success(result);
    }

    @PostMapping("/emergency/add-for-elder")
    public Result addEmergencyContactForElder(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        Long elderId = Long.valueOf(params.get("elderId").toString());
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
            return elderId.equals(friendId);
        });

        if (!isRelated) {
            return Result.error("您没有权限为该长辈添加紧急联系人");
        }

        boolean contactIsFamily = friends.stream().anyMatch(f -> {
            Object id = f.get("id");
            Long friendId = null;
            if (id instanceof Number) {
                friendId = ((Number) id).longValue();
            }
            return contactUserId.equals(friendId);
        });

        if (!contactIsFamily && !contactUserId.equals(me.getId())) {
            return Result.error("紧急联系人必须是您的家人");
        }

        if (emergencyContactMapper.countRelation(elderId, contactUserId) > 0) {
            EmergencyContact existing = emergencyContactMapper.selectByUserIdAndContactUserId(elderId, contactUserId);
            existing.setContactName(contactName);
            existing.setContactPhone(contactPhone);
            existing.setUpdateTime(LocalDateTime.now());
            emergencyContactMapper.update(existing);
            return Result.success("更新成功");
        }

        EmergencyContact contact = new EmergencyContact();
        contact.setUserId(elderId);
        contact.setContactUserId(contactUserId);
        contact.setContactName(contactName);
        contact.setContactPhone(contactPhone);
        contact.setIsStaff(0);
        contact.setIsSystemAdd(0);
        contact.setPriority(1);
        contact.setUpdateTime(LocalDateTime.now());

        emergencyContactMapper.insert(contact);
        return Result.success("添加成功");
    }

    @PostMapping("/emergency/remove-for-elder")
    public Result removeEmergencyContactForElder(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        Long elderId = Long.valueOf(params.get("elderId").toString());
        Long contactId = Long.valueOf(params.get("contactId").toString());

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        boolean isRelated = friends.stream().anyMatch(f -> {
            Object id = f.get("id");
            Long friendId = null;
            if (id instanceof Number) {
                friendId = ((Number) id).longValue();
            }
            return elderId.equals(friendId);
        });

        if (!isRelated) {
            return Result.error("您没有权限操作");
        }

        EmergencyContact contact = emergencyContactMapper.selectById(contactId);
        if (contact != null && contact.getUserId().equals(elderId) && contact.getIsSystemAdd() == 0) {
            emergencyContactMapper.deleteById(contactId);
        }

        return Result.success("移除成功");
    }

    @PostMapping("/emergency/request")
    public Result requestToBeEmergencyContact(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        Long elderId = Long.valueOf(params.get("elderId").toString());
        
        System.out.println("【紧急联系人申请】子女 " + me.getNickname() + "(ID:" + me.getId() + ") 申请成为长辈(ID:" + elderId + ") 的紧急联系人");
        
        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        boolean isRelated = friends.stream().anyMatch(f -> {
            Object id = f.get("id");
            Long friendId = null;
            if (id instanceof Number) {
                friendId = ((Number) id).longValue();
            }
            return elderId.equals(friendId);
        });

        if (!isRelated) {
            return Result.error("您与该长辈不是亲友关系");
        }

        if (emergencyContactMapper.countRelation(elderId, me.getId()) > 0) {
            return Result.error("您已是该长辈的紧急联系人");
        }

        Map<String, Object> notice = new HashMap<>();
        notice.put("msgType", "EMERGENCY_CONTACT_REQUEST");
        notice.put("fromId", me.getId());
        notice.put("fromName", me.getNickname());
        notice.put("fromAvatar", me.getAvatar());
        notice.put("timestamp", System.currentTimeMillis());
        
        System.out.println("【紧急联系人申请】发送WebSocket通知到长辈: " + elderId + ", 消息: " + notice);
        chatHandler.sendToUser(elderId, notice);
        
        return Result.success("申请已发送，等待长辈确认");
    }

    @PostMapping("/emergency/request/handle")
    public Result handleEmergencyRequest(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        Long requesterId = Long.valueOf(params.get("requesterId").toString());
        Boolean accept = Boolean.valueOf(params.get("accept").toString());

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        boolean isRelated = friends.stream().anyMatch(f -> {
            Object id = f.get("id");
            Long friendId = null;
            if (id instanceof Number) {
                friendId = ((Number) id).longValue();
            }
            return requesterId.equals(friendId);
        });

        if (!isRelated) {
            return Result.error("该用户不是您的亲友");
        }

        if (accept) {
            User requester = userMapper.getById(requesterId);
            String contactName = requester != null ? requester.getNickname() : "";
            UserProfile requesterProfile = userProfileMapper.selectById(requesterId);
            String contactPhone = requesterProfile != null ? requesterProfile.getUserPhone() : "";

            if (emergencyContactMapper.countRelation(me.getId(), requesterId) > 0) {
                return Result.error("该用户已是您的紧急联系人");
            }

            EmergencyContact contact = new EmergencyContact();
            contact.setUserId(me.getId());
            contact.setContactUserId(requesterId);
            contact.setContactName(contactName);
            contact.setContactPhone(contactPhone);
            contact.setIsStaff(0);
            contact.setIsSystemAdd(0);
            contact.setPriority(1);
            contact.setUpdateTime(LocalDateTime.now());

            emergencyContactMapper.insert(contact);

            Map<String, Object> notice = new HashMap<>();
            notice.put("msgType", "EMERGENCY_CONTACT_ACCEPTED");
            notice.put("elderId", me.getId());
            notice.put("elderName", me.getNickname());
            chatHandler.sendToUser(requesterId, notice);

            return Result.success("已同意，紧急联系人添加成功");
        } else {
            Map<String, Object> notice = new HashMap<>();
            notice.put("msgType", "EMERGENCY_CONTACT_REJECTED");
            notice.put("elderId", me.getId());
            notice.put("elderName", me.getNickname());
            chatHandler.sendToUser(requesterId, notice);

            return Result.success("已拒绝");
        }
    }

    @GetMapping("/emergency/my-elders")
    public Result getMyEldersAsEmergency(HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        
        List<Map<String, Object>> eldersWhereIAmEmergency = friends.stream()
                .filter(f -> "older".equals(f.get("role")))
                .filter(f -> {
                    Long elderId = ((Number) f.get("id")).longValue();
                    return emergencyContactMapper.countRelation(elderId, me.getId()) > 0;
                })
                .map(f -> {
                    Map<String, Object> elder = new HashMap<>(f);
                    Long elderId = ((Number) f.get("id")).longValue();
                    elder.put("isEmergency", true);
                    UserProfile profile = userProfileMapper.selectById(elderId);
                    if (profile != null) {
                        elder.put("phone", profile.getUserPhone());
                    }
                    return elder;
                })
                .collect(Collectors.toList());

        return Result.success(eldersWhereIAmEmergency);
    }

    @GetMapping("/emergency/elders-where-i-am-emergency")
    public Result getEldersWhereIAmEmergency(HttpSession session) {
        User me = (User) session.getAttribute("currUser");
        if (me == null) return Result.error("请先登录");

        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        
        Map<Long, Boolean> elderEmergencyMap = new HashMap<>();
        
        friends.stream()
                .filter(f -> "older".equals(f.get("role")))
                .forEach(f -> {
                    Long elderId = ((Number) f.get("id")).longValue();
                    boolean isEmergency = emergencyContactMapper.countRelation(elderId, me.getId()) > 0;
                    elderEmergencyMap.put(elderId, isEmergency);
                });

        return Result.success(elderEmergencyMap);
    }
}

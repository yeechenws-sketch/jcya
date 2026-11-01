package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.*;
import com.chat.older.xiechen.model.*;
import com.chat.older.xiechen.vo.UserVO;
import com.chat.older.xiechen.handler.ChatHandler;
import com.chat.older.xiechen.service.CommunityChangeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/friend")
public class FriendController {

    @Autowired
    private FriendMapper friendMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ChatHandler chatHandler;

    @Autowired
    private CommunityChangeService communityChangeService;

    @Autowired
    private EmergencyContactMapper emergencyContactMapper;

    @Autowired
    private CommunityMapper communityMapper;

    @Autowired
    private UserProfileMapper userProfileMapper;

    private User getLoginUser(HttpSession session) {
        return (User) session.getAttribute("currUser");
    }

    @GetMapping("/list")
    public Result getFriends(HttpSession session) {
        User me = getLoginUser(session);
        if (me == null) return Result.error("请先登录");
        List<Friendship> friendships = friendMapper.getFriendshipsByUserId(me.getId());
        return Result.success(friendships);
    }

    @GetMapping("/list/ordered")
    public Result getFriendsOrdered(HttpSession session) {
        User me = getLoginUser(session);
        if (me == null) return Result.error("请先登录");
        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        return Result.success(friends);
    }

    @PostMapping("/request")
    public Result sendRequest(@RequestBody Map<String, String> params, HttpSession session) {
        User me = getLoginUser(session);
        if (me == null) return Result.error("请先登录");

        String username = params.get("username");
        User targetUser = userMapper.findByUsername(username);

        if (targetUser == null) return Result.error("找不到该用户");
        if (targetUser.getId().equals(me.getId())) return Result.error("不能添加自己");
        if (friendMapper.countRelation(me.getId(), targetUser.getId()) > 0) return Result.error("已是亲友或申请中");

        friendMapper.saveRequest(me.getId(), targetUser.getId(), targetUser.getNickname());

        Map<String, Object> notice = new HashMap<>();
        notice.put("fromId", me.getId());
        notice.put("toId", targetUser.getId());
        notice.put("msgType", "FRIEND_REQUEST");
        notice.put("content", "有人申请添加你为亲友");

        chatHandler.sendToUser(targetUser.getId(), notice);

        return Result.success("申请已发送");
    }

    @GetMapping("/pending")
    public Result getPending(HttpSession session) {
        User me = getLoginUser(session);
        if (me == null) return Result.error("请先登录");
        return Result.success(friendMapper.getPendingRequests(me.getId()));
    }

    @PostMapping("/handle")
    @Transactional
    public Result handleRequest(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = getLoginUser(session);
        if (me == null) return Result.error("请先登录");

        Long requestId = Long.valueOf(params.get("requestId").toString());
        Integer status = Integer.valueOf(params.get("status").toString());

        Friendship request = friendMapper.getById(requestId);
        if (request == null || !request.getFriendId().equals(me.getId())) return Result.error("非法操作");

        if (status == 1) {
            friendMapper.updateStatus(requestId, 1);
            User applicant = userMapper.getById(request.getUserId());
            friendMapper.saveFriend(me.getId(), request.getUserId(), applicant != null ? applicant.getNickname() : "亲友", 1);

            Map<String, Object> notice = new HashMap<>();
            notice.put("fromId", me.getId());
            notice.put("toId", request.getUserId());
            notice.put("msgType", "FRIEND_ACCEPT");
            chatHandler.sendToUser(request.getUserId(), notice);

            List<EmergencyContact> staffContacts = emergencyContactMapper.selectByUserId(me.getId());
            for (EmergencyContact contact : staffContacts) {
                if (contact.getIsStaff() == 1 && contact.getIsSystemAdd() == 1) {
                    if (contact.getContactUserId().equals(request.getUserId())) {
                        List<Community> communities = communityMapper.selectAll();
                        for (Community community : communities) {
                            if (community.getAdminUserId().equals(request.getUserId())) {
                                communityChangeService.sendWelcomeMessage(request.getUserId(), me.getId(), community.getId());
                                UserProfile userProfile = userProfileMapper.selectById(me.getId());
                                if (userProfile != null && userProfile.getRealName() != null && !userProfile.getRealName().isEmpty()) {
                                    friendMapper.updateRemark(request.getUserId(), me.getId(), userProfile.getRealName());
                                }
                                break;
                            }
                        }
                        break;
                    }
                }
            }

            return Result.success("添加成功");
        } else {
            friendMapper.updateStatus(requestId, 0);
            return Result.success("已拒绝");
        }
    }

    @PostMapping("/updateRemark")
    public Result updateRemark(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = getLoginUser(session);
        if (me == null) return Result.error("请先登录");
        Long friendId = Long.valueOf(params.get("friendId").toString());
        String remark = params.get("remark") != null ? params.get("remark").toString() : "";
        String relationship = params.get("relationship") != null ? params.get("relationship").toString() : null;
        
        if (relationship != null && !relationship.isEmpty()) {
            if (!isValidRelationship(relationship)) {
                return Result.error("无效的亲属关系");
            }
            friendMapper.updateRemarkAndRelationship(me.getId(), friendId, remark, relationship);
        } else {
            friendMapper.updateRemark(me.getId(), friendId, remark);
        }
        return Result.success("修改成功");
    }

    @PostMapping("/updateRelationship")
    public Result updateRelationship(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = getLoginUser(session);
        if (me == null) return Result.error("请先登录");
        Long friendId = Long.valueOf(params.get("friendId").toString());
        String relationship = params.get("relationship") != null ? params.get("relationship").toString() : null;
        
        Friendship friendship = friendMapper.getFriendship(me.getId(), friendId);
        if (friendship == null) {
            return Result.error("亲友关系不存在");
        }
        
        if (relationship != null && !relationship.isEmpty() && !isValidRelationship(relationship)) {
            return Result.error("无效的亲属关系");
        }
        
        String remark = friendship.getRemark() != null ? friendship.getRemark() : "";
        friendMapper.updateRemarkAndRelationship(me.getId(), friendId, remark, relationship);
        return Result.success("亲属关系更新成功");
    }

    private boolean isValidRelationship(String relationship) {
        if (relationship == null || relationship.isEmpty()) {
            return true;
        }
        return relationship.equals("父亲") || relationship.equals("母亲") || 
               relationship.equals("爷爷") || relationship.equals("奶奶") ||
               relationship.equals("外公") || relationship.equals("外婆");
    }

    @PostMapping("/delete")
    @Transactional
    public Result deleteFriend(@RequestBody Map<String, Object> params, HttpSession session) {
        User me = getLoginUser(session);
        if (me == null) return Result.error("请先登录");

        Long friendId = Long.valueOf(params.get("friendId").toString());

        friendMapper.deleteFriend(me.getId(), friendId);

        Map<String, Object> notice = new HashMap<>();
        notice.put("fromId", me.getId());
        notice.put("toId", friendId);
        notice.put("msgType", "FRIEND_DELETE");

        chatHandler.sendToUser(friendId, notice);

        return Result.success("已解除亲友关系");
    }

    @GetMapping("/online-status")
    public Result getFriendsOnlineStatus(HttpSession session) {
        User me = getLoginUser(session);
        if (me == null) return Result.error("请先登录");
        
        List<Map<String, Object>> friends = friendMapper.getMyFriendsOrderByLastMessage(me.getId());
        Map<Long, Boolean> onlineStatus = new HashMap<>();
        
        for (Map<String, Object> friend : friends) {
            Long friendId = ((Number) friend.get("id")).longValue();
            onlineStatus.put(friendId, ChatHandler.isUserOnline(friendId));
        }
        
        return Result.success(onlineStatus);
    }
}
package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.CommunityMapper;
import com.chat.older.xiechen.dao.UserCommunityMapper;
import com.chat.older.xiechen.dao.UserMapper;
import com.chat.older.xiechen.model.Community;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.model.UserCommunity;
import com.chat.older.xiechen.service.CommunityService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/community")
public class CommunityController {

    @Autowired
    private CommunityMapper communityMapper;

    @Autowired
    private CommunityService communityService;

    @Autowired
    private UserCommunityMapper userCommunityMapper;

    @Autowired
    private UserMapper userMapper;

    @GetMapping("/list")
    public Result getCommunityList() {
        List<Community> communities = communityMapper.selectAll();
        return Result.success(communities);
    }

    @GetMapping("/current")
    public Result getCurrentResidence(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        UserCommunity residence = communityService.getCurrentResidence(user.getId());
        if (residence != null) {
            Community community = communityMapper.selectById(residence.getCommunityId());
            String adminName = null;
            if (community != null && community.getAdminUserId() != null) {
                User adminUser = userMapper.getById(community.getAdminUserId());
                if (adminUser != null) {
                    adminName = adminUser.getNickname();
                }
            }
            return Result.success(Map.of(
                    "residence", residence,
                    "community", community,
                    "adminName", adminName
            ));
        }
        return Result.success(null);
    }

    @PostMapping("/moveIn")
    public Result moveIn(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        try {
            Integer communityId = Integer.valueOf(params.get("communityId").toString());
            communityService.moveIn(user.getId(), communityId);
            return Result.success("入住成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    @PostMapping("/switch")
    public Result switchCommunity(@RequestBody Map<String, Object> params, HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user == null) return Result.error("未登录");

        try {
            Integer newCommunityId = Integer.valueOf(params.get("newCommunityId").toString());
            communityService.moveOutAndMoveIn(user.getId(), newCommunityId);
            return Result.success("社区切换成功");
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }
}

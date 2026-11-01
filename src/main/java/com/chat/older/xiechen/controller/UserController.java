package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.UserMapper;
import com.chat.older.xiechen.model.User;
import com.chat.older.xiechen.model.UserDisplaySettings;
import com.chat.older.xiechen.service.UploadService;
import com.chat.older.xiechen.service.UserService;
import com.chat.older.xiechen.service.UserDisplaySettingsService;
import com.chat.older.xiechen.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/user")
public class UserController {
    @Autowired
    private UserService userService;
    @Resource
    UserMapper userMapper;
    @Autowired
    private UploadService uploadService;
    @Autowired
    private UserDisplaySettingsService userDisplaySettingsService;
    @Autowired
    private SystemConfigService systemConfigService;
    
    @PostMapping("/register")
    public Result register(@RequestBody User user) {
        if (!systemConfigService.isRegistrationEnabled()) {
            return Result.error("注册功能已关闭，请联系管理员");
        }
        return userService.register(user);
    }
    @PostMapping("/login")
    public Result login(@RequestBody User user, HttpSession session) {
        return userService.login(user, session);
    }
    @GetMapping("/info")
    public Result getUserInfo(HttpSession session) {
        User user = (User) session.getAttribute("currUser");
        if (user != null) {
            return Result.success(user);
        }
        return Result.error("未登录");
    }
    
    @GetMapping("/display-settings")
    public Result getDisplaySettings(@RequestParam Long userId, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        UserDisplaySettings settings = userDisplaySettingsService.getSettingsByUserId(userId);
        if (settings == null) {
            settings = new UserDisplaySettings(userId, 100, 80, 0);
        }
        return Result.success(settings);
    }
    
    @PostMapping("/display-settings")
    public Result saveDisplaySettings(@RequestBody UserDisplaySettings settings, HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return Result.error("未登录");
        }
        
        userDisplaySettingsService.saveSettings(settings);
        return Result.success("设置保存成功");
    }
    
    @GetMapping("/contacts")
    public Result getContacts(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) return Result.error("未登录");

        List<User> friends = userMapper.getMyFriends(currUser.getId());
        return Result.success(friends);
    }
    @GetMapping("/redirect")
    public ModelAndView redirectAfterLogin(HttpSession session) {
        User currUser = (User) session.getAttribute("currUser");
        if (currUser == null) {
            return new ModelAndView("redirect:/login.html");
        }
        User user = userMapper.getById(currUser.getId());
        if (user != null && "admin".equals(user.getRole())) {
            return new ModelAndView("redirect:/admin_index.html");
        }
        return new ModelAndView("redirect:/index.html");
    }

    @PostMapping("/logout")
    public Result<String> logout(HttpSession session) {
        try {
            session.invalidate();
            return Result.success("退出成功");
        } catch (Exception e) {
            return Result.success("退出成功");
        }
    }

    @PostMapping("/avatar")
    public Result<Map<String, Object>> updateAvatar(@RequestParam("file") MultipartFile file, HttpSession session) {
        Map<String, Object> result = new HashMap<>();
        try {
            User currUser = (User) session.getAttribute("currUser");
            if (currUser == null) {
                return Result.error("未登录");
            }

            if (file.isEmpty()) {
                return Result.error("文件不能为空");
            }

            String contentType = file.getContentType();
            if (contentType == null || !contentType.startsWith("image/")) {
                return Result.error("只能上传图片文件");
            }

            String url = uploadService.upload(file.getInputStream(), file.getOriginalFilename());
            if (url == null) {
                return Result.error("上传失败");
            }

            userMapper.updateAvatar(currUser.getId(), url);

            currUser.setAvatar(url);
            session.setAttribute("currUser", currUser);

            Map<String, Object> data = new HashMap<>();
            data.put("url", url);
            return Result.success(data);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("上传失败: " + e.getMessage());
        }
    }
}
package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.model.dto.ProfileSettingsDTO;
import com.chat.older.xiechen.service.ProfileSettingsService;
import com.chat.older.xiechen.vo.ProfileSettingsVO;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpSession;

@RestController
@RequestMapping("/api/profile")
@RequiredArgsConstructor
public class ProfileSettingsController {

    private static final Logger logger = LoggerFactory.getLogger(ProfileSettingsController.class);

    private final ProfileSettingsService profileSettingsService;

    @GetMapping("/settings")
    public Result<ProfileSettingsVO> getSettings(HttpSession session) {
        Object currUserObj = session.getAttribute("currUser");
        if (currUserObj == null) {
            return Result.error("未登录");
        }
        Long userId = ((com.chat.older.xiechen.model.User) currUserObj).getId();

        try {
            ProfileSettingsVO settings = profileSettingsService.getSettings(userId);
            return Result.success(settings);
        } catch (Exception e) {
            return Result.error("获取设置失败：" + e.getMessage());
        }
    }

    @PostMapping("/settings")
    public Result<String> saveSettings(@RequestBody ProfileSettingsDTO dto, HttpSession session) {
        logger.info("开始保存个人信息设置");

        Object currUserObj = session.getAttribute("currUser");
        if (currUserObj == null) {
            logger.error("未登录");
            return Result.error("未登录");
        }

        Long userId = ((com.chat.older.xiechen.model.User) currUserObj).getId();
        logger.info("用户ID: {}", userId);
        logger.info("请求参数: {}", dto);

        try {
            logger.info("调用profileSettingsService.saveProfile方法");
            profileSettingsService.saveProfile(userId, dto);
            logger.info("保存成功");
            return Result.success("保存成功");
        } catch (Exception e) {
            logger.error("保存失败", e);
            String errorMessage = e.getMessage() != null ? e.getMessage() : "未知错误"; 
            logger.error("错误信息: {}", errorMessage);
            return Result.error("保存失败：" + errorMessage);
        }
    }
}

package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.service.BaiduAsrService;
import com.chat.older.xiechen.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/asr")
public class AsrController {

    @Autowired
    private BaiduAsrService baiduAsrService;

    @Autowired
    private SystemConfigService systemConfigService;

    @PostMapping("/translate")
    public Result translateToText(@RequestParam("file") MultipartFile file) {
        if (!systemConfigService.isAsrEnabled()) {
            return Result.error("语音识别功能已关闭");
        }
        try {
            if (file.isEmpty()) return Result.error("音频文件为空");

            byte[] bytes = file.getBytes();
            String text = baiduAsrService.transcribe(bytes);

            if (text == null || text.isEmpty()) {
                return Result.error("未识别到文字");
            }
            return Result.success(text);
        } catch (Exception e) {
            return Result.error("识别过程出错: " + e.getMessage());
        }
    }
}
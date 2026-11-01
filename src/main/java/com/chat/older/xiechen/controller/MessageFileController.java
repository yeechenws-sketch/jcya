package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.service.UploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.HashMap;
import java.util.Map;

@RestController
public class MessageFileController {

    @Autowired
    private UploadService uploadService;

    @PostMapping("/api/common/upload")
    public Map<String, Object> uploadVoice(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (file.isEmpty()) {
                result.put("code", 400);
                result.put("msg", "文件不能为空");
                return result;
            }

            String url = uploadService.upload(file.getInputStream(), file.getOriginalFilename());

            if (url != null) {
                result.put("code", 200);
                result.put("url", url);
            } else {
                result.put("code", 500);
                result.put("msg", "上传七牛云失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "后端接口异常");
        }
        return result;
    }

    @PostMapping("/api/upload")
    public Map<String, Object> uploadFile(@RequestParam("file") MultipartFile file) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (file.isEmpty()) {
                result.put("code", 400);
                result.put("msg", "文件不能为空");
                return result;
            }

            String url = uploadService.upload(file.getInputStream(), file.getOriginalFilename());

            if (url != null) {
                result.put("code", 200);
                result.put("url", url);
            } else {
                result.put("code", 500);
                result.put("msg", "上传失败");
            }
        } catch (Exception e) {
            e.printStackTrace();
            result.put("code", 500);
            result.put("msg", "后端接口异常");
        }
        return result;
    }
}
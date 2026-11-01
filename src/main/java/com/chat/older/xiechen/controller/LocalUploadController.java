package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.service.LocalStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/api/upload")
public class LocalUploadController {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalUploadController.class);
    
    @Autowired
    private LocalStorageService localStorageService;
    
    @Value("${storage.local.max-size:52428800}")
    private long maxFileSize;
    
    private static final Map<String, String[]> ALLOWED_TYPES = new HashMap<>();
    
    static {
        ALLOWED_TYPES.put("image", new String[]{"jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"});
        ALLOWED_TYPES.put("video", new String[]{"mp4", "avi", "mov", "wmv", "flv", "mkv", "webm"});
        ALLOWED_TYPES.put("audio", new String[]{"mp3", "wav", "ogg", "aac", "flac", "m4a", "amr"});
        ALLOWED_TYPES.put("document", new String[]{"pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt"});
    }
    
    @PostMapping("/image")
    public ResponseEntity<Map<String, Object>> uploadImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "general") String category) {
        return uploadFile(file, "image", category);
    }
    
    @PostMapping("/video")
    public ResponseEntity<Map<String, Object>> uploadVideo(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "general") String category) {
        return uploadFile(file, "video", category);
    }
    
    @PostMapping("/audio")
    public ResponseEntity<Map<String, Object>> uploadAudio(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "category", defaultValue = "general") String category) {
        return uploadFile(file, "audio", category);
    }
    
    @PostMapping("/avatar")
    public ResponseEntity<Map<String, Object>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        return uploadFile(file, "image", "avatar");
    }
    
    @PostMapping("/ringtone")
    public ResponseEntity<Map<String, Object>> uploadRingtone(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "message") String type) {
        return uploadFile(file, "audio", "ringtone/" + type);
    }
    
    @PostMapping("/resource")
    public ResponseEntity<Map<String, Object>> uploadResource(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "type", defaultValue = "image") String type,
            @RequestParam(value = "category", defaultValue = "general") String category) {
        return uploadFile(file, type, category);
    }
    
    @PostMapping("/general")
    public ResponseEntity<Map<String, Object>> uploadGeneral(@RequestParam("file") MultipartFile file) {
        String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
        String type = detectFileType(extension);
        return uploadFile(file, type, "general");
    }
    
    private ResponseEntity<Map<String, Object>> uploadFile(MultipartFile file, String type, String category) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            if (file == null || file.isEmpty()) {
                result.put("success", false);
                result.put("message", "文件不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            if (file.getSize() > maxFileSize) {
                result.put("success", false);
                result.put("message", "文件大小超出限制 (最大 " + (maxFileSize / 1024 / 1024) + "MB)");
                return ResponseEntity.badRequest().body(result);
            }
            
            String extension = getFileExtension(file.getOriginalFilename()).toLowerCase();
            
            if (!validateFileType(extension, type)) {
                result.put("success", false);
                result.put("message", "不支持的文件类型: " + extension + "，期望类型: " + type);
                return ResponseEntity.badRequest().body(result);
            }
            
            String url;
            switch (type) {
                case "image":
                    url = localStorageService.uploadImage(file.getInputStream(), file.getOriginalFilename());
                    break;
                case "video":
                    url = localStorageService.uploadVideo(file.getInputStream(), file.getOriginalFilename());
                    break;
                case "audio":
                    url = localStorageService.uploadAudio(file.getInputStream(), file.getOriginalFilename());
                    break;
                default:
                    url = localStorageService.upload(file.getInputStream(), file.getOriginalFilename(), category);
            }
            
            if (url != null) {
                result.put("success", true);
                result.put("url", url);
                result.put("filename", file.getOriginalFilename());
                result.put("size", file.getSize());
                result.put("type", type);
                logger.info("[本地上传] 成功: {} -> {}", file.getOriginalFilename(), url);
                return ResponseEntity.ok(result);
            } else {
                result.put("success", false);
                result.put("message", "文件保存失败");
                return ResponseEntity.internalServerError().body(result);
            }
            
        } catch (IOException e) {
            logger.error("[本地上传] 失败: {}", e.getMessage(), e);
            result.put("success", false);
            result.put("message", "上传失败: " + e.getMessage());
            return ResponseEntity.internalServerError().body(result);
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(lastDot + 1);
        }
        return "";
    }
    
    private boolean validateFileType(String extension, String type) {
        String[] allowed = ALLOWED_TYPES.get(type);
        if (allowed == null) {
            return true;
        }
        for (String ext : allowed) {
            if (ext.equalsIgnoreCase(extension)) {
                return true;
            }
        }
        return false;
    }
    
    private String detectFileType(String extension) {
        for (Map.Entry<String, String[]> entry : ALLOWED_TYPES.entrySet()) {
            for (String ext : entry.getValue()) {
                if (ext.equalsIgnoreCase(extension)) {
                    return entry.getKey();
                }
            }
        }
        return "document";
    }
}

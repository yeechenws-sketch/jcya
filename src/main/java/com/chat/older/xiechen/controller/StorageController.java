package com.chat.older.xiechen.controller;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@RestController
@RequestMapping("/api/storage")
public class StorageController {
    
    private static final Logger logger = LoggerFactory.getLogger(StorageController.class);
    
    @Value("${storage.local.path:/app/uploads}")
    private String storagePath;
    
    @Value("${storage.local.baseUrl:https://xiechen.xyz/uploads}")
    private String baseUrl;
    
    @GetMapping("/files")
    public ResponseEntity<Map<String, Object>> listFiles(
            @RequestParam(value = "path", defaultValue = "") String subPath,
            @RequestParam(value = "search", defaultValue = "") String search,
            @RequestParam(value = "type", defaultValue = "") String fileType) {
        
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        
        try {
            Path basePath = Paths.get(storagePath);
            
            if (!Files.exists(basePath)) {
                logger.warn("[存储管理] 存储目录不存在: {}", storagePath);
                result.put("success", true);
                result.put("items", items);
                result.put("total", 0);
                result.put("message", "存储目录不存在: " + storagePath);
                return ResponseEntity.ok(result);
            }
            
            Files.walk(basePath)
                .filter(p -> Files.isRegularFile(p))
                .forEach(p -> {
                    String filename = p.getFileName().toString();
                    
                    if (!search.isEmpty() && !filename.toLowerCase().contains(search.toLowerCase())) {
                        return;
                    }
                    
                    String relativePath = basePath.relativize(p).toString().replace("\\", "/");
                    String fileExt = getFileExtension(filename).toLowerCase();
                    
                    if (!fileType.isEmpty()) {
                        String actualType = getFileType(filename);
                        if (!actualType.equals(fileType)) {
                            return;
                        }
                    }
                    
                    try {
                        Map<String, Object> fileInfo = new HashMap<>();
                        fileInfo.put("name", filename);
                        fileInfo.put("path", relativePath);
                        fileInfo.put("size", Files.size(p));
                        fileInfo.put("lastModified", Files.getLastModifiedTime(p).toMillis());
                        fileInfo.put("url", baseUrl + "/" + relativePath);
                        fileInfo.put("type", getFileType(filename));
                        fileInfo.put("extension", fileExt);
                        
                        String parentDir = p.getParent().getFileName().toString();
                        fileInfo.put("category", parentDir);
                        
                        items.add(fileInfo);
                    } catch (Exception e) {
                        logger.error("[存储管理] 读取文件信息失败: {}", p, e);
                    }
                });
            
            items.sort((a, b) -> {
                long timeA = (long) a.get("lastModified");
                long timeB = (long) b.get("lastModified");
                return Long.compare(timeB, timeA);
            });
            
            result.put("success", true);
            result.put("items", items);
            result.put("total", items.size());
            result.put("storagePath", storagePath);
            
            logger.info("[存储管理] 列出文件: {} 个", items.size());
            
        } catch (Exception e) {
            logger.error("[存储管理] 获取文件列表失败", e);
            result.put("success", false);
            result.put("message", "获取文件列表失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Path basePath = Paths.get(storagePath);
            
            if (!Files.exists(basePath)) {
                result.put("success", true);
                result.put("totalFiles", 0);
                result.put("totalDirs", 0);
                result.put("totalSize", 0);
                result.put("totalSizeFormatted", "0 B");
                result.put("todayUpload", 0);
                result.put("todayUploadSize", 0);
                result.put("todayUploadSizeFormatted", "0 B");
                return ResponseEntity.ok(result);
            }
            
            long totalSize = 0;
            int totalFiles = 0;
            int totalDirs = 0;
            int todayUpload = 0;
            long todayUploadSize = 0;
            
            LocalDate today = LocalDate.now();
            long todayStart = today.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
            
            File[] categoryDirs = basePath.toFile().listFiles(File::isDirectory);
            if (categoryDirs != null) {
                totalDirs = categoryDirs.length;
            }
            
            Iterator<Path> it = Files.walk(basePath).iterator();
            while (it.hasNext()) {
                Path p = it.next();
                if (Files.isRegularFile(p)) {
                    totalFiles++;
                    long size = Files.size(p);
                    totalSize += size;
                    
                    long lastModified = Files.getLastModifiedTime(p).toMillis();
                    if (lastModified >= todayStart) {
                        todayUpload++;
                        todayUploadSize += size;
                    }
                }
            }
            
            Map<String, Long> categoryStats = new HashMap<>();
            String[] categories = {"images", "videos", "audios", "avatars", "files"};
            for (String cat : categories) {
                Path catPath = basePath.resolve(cat);
                if (Files.exists(catPath)) {
                    long catSize = Files.walk(catPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(fp -> {
                            try { return Files.size(fp); } catch (Exception e) { return 0; }
                        })
                        .sum();
                    categoryStats.put(cat, catSize);
                }
            }
            
            result.put("success", true);
            result.put("totalFiles", totalFiles);
            result.put("totalDirs", totalDirs);
            result.put("totalSize", totalSize);
            result.put("totalSizeFormatted", formatSize(totalSize));
            result.put("todayUpload", todayUpload);
            result.put("todayUploadSize", todayUploadSize);
            result.put("todayUploadSizeFormatted", formatSize(todayUploadSize));
            result.put("categoryStats", categoryStats);
            result.put("storagePath", storagePath);
            
            logger.info("[存储管理] 统计: 文件={}, 大小={}, 今日上传={}", totalFiles, formatSize(totalSize), todayUpload);
            
        } catch (Exception e) {
            logger.error("[存储管理] 获取统计信息失败", e);
            result.put("success", false);
            result.put("message", "获取统计信息失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    @GetMapping("/categories")
    public ResponseEntity<Map<String, Object>> getCategories() {
        Map<String, Object> result = new HashMap<>();
        
        try {
            Path basePath = Paths.get(storagePath);
            
            if (!Files.exists(basePath)) {
                result.put("success", true);
                result.put("categories", new ArrayList<>());
                return ResponseEntity.ok(result);
            }
            
            List<Map<String, Object>> categories = new ArrayList<>();
            String[] defaultCategories = {"images", "videos", "audios", "avatars", "files"};
            
            for (String cat : defaultCategories) {
                Path catPath = basePath.resolve(cat);
                if (Files.exists(catPath)) {
                    int fileCount = (int) Files.walk(catPath)
                        .filter(Files::isRegularFile)
                        .count();
                    
                    long size = Files.walk(catPath)
                        .filter(Files::isRegularFile)
                        .mapToLong(p -> {
                            try { return Files.size(p); } catch (Exception e) { return 0; }
                        })
                        .sum();
                    
                    Map<String, Object> catInfo = new HashMap<>();
                    catInfo.put("name", cat);
                    catInfo.put("displayName", getCategoryDisplayName(cat));
                    catInfo.put("fileCount", fileCount);
                    catInfo.put("size", size);
                    catInfo.put("sizeFormatted", formatSize(size));
                    categories.add(catInfo);
                }
            }
            
            result.put("success", true);
            result.put("categories", categories);
            
        } catch (Exception e) {
            logger.error("[存储管理] 获取分类信息失败", e);
            result.put("success", false);
            result.put("message", "获取分类信息失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    @DeleteMapping("/file")
    public ResponseEntity<Map<String, Object>> deleteFile(@RequestBody Map<String, String> request) {
        Map<String, Object> result = new HashMap<>();
        
        try {
            String filePath = request.get("path");
            if (filePath == null || filePath.isEmpty()) {
                result.put("success", false);
                result.put("message", "文件路径不能为空");
                return ResponseEntity.badRequest().body(result);
            }
            
            Path basePath = Paths.get(storagePath);
            Path targetPath = basePath.resolve(filePath).normalize();
            
            if (!targetPath.startsWith(basePath)) {
                result.put("success", false);
                result.put("message", "非法路径");
                return ResponseEntity.badRequest().body(result);
            }
            
            if (!Files.exists(targetPath)) {
                result.put("success", false);
                result.put("message", "文件不存在");
                return ResponseEntity.badRequest().body(result);
            }
            
            Files.delete(targetPath);
            logger.info("[存储管理] 删除文件: {}", targetPath);
            
            result.put("success", true);
            result.put("message", "删除成功");
            
        } catch (Exception e) {
            logger.error("[存储管理] 删除文件失败", e);
            result.put("success", false);
            result.put("message", "删除失败: " + e.getMessage());
        }
        
        return ResponseEntity.ok(result);
    }
    
    private String getFileType(String filename) {
        String ext = getFileExtension(filename).toLowerCase();
        if (ext.matches("jpg|jpeg|png|gif|webp|bmp|svg")) {
            return "image";
        } else if (ext.matches("mp4|avi|mov|wmv|flv|mkv|webm")) {
            return "video";
        } else if (ext.matches("mp3|wav|ogg|aac|flac|m4a|amr")) {
            return "audio";
        }
        return "file";
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
    
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
    }
    
    private String getCategoryDisplayName(String category) {
        switch (category) {
            case "images": return "图片";
            case "videos": return "视频";
            case "audios": return "音频";
            case "avatars": return "头像";
            case "files": return "其他文件";
            default: return category;
        }
    }
}

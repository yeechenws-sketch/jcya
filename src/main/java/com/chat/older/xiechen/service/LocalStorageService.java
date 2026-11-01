package com.chat.older.xiechen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class LocalStorageService {
    
    private static final Logger logger = LoggerFactory.getLogger(LocalStorageService.class);
    
    @Value("${storage.local.path:/app/uploads}")
    private String storagePath;
    
    @Value("${storage.local.baseUrl:https://xiechen.xyz/uploads}")
    private String baseUrl;
    
    @PostConstruct
    public void init() {
        try {
            Path uploadPath = Paths.get(storagePath);
            if (!Files.exists(uploadPath)) {
                Files.createDirectories(uploadPath);
                logger.info("[本地存储] 创建存储目录: {}", storagePath);
            }
            
            String[] subDirs = {"images", "videos", "audios", "avatars", "files"};
            for (String dir : subDirs) {
                Path subPath = uploadPath.resolve(dir);
                if (!Files.exists(subPath)) {
                    Files.createDirectories(subPath);
                    logger.info("[本地存储] 创建子目录: {}", subPath);
                }
            }
            
            logger.info("[本地存储] 初始化完成，存储路径: {}, 访问地址: {}", storagePath, baseUrl);
        } catch (Exception e) {
            logger.error("[本地存储] 初始化失败: {}", e.getMessage(), e);
        }
    }
    
    public String upload(InputStream inputStream, String filename, String category) {
        try {
            String datePath = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy/MM"));
            String subDir = getSubDir(filename, category);
            
            Path categoryPath = Paths.get(storagePath, subDir, datePath);
            if (!Files.exists(categoryPath)) {
                Files.createDirectories(categoryPath);
            }
            
            String extension = getFileExtension(filename);
            String newFilename = UUID.randomUUID().toString().replace("-", "") + extension;
            
            Path filePath = categoryPath.resolve(newFilename);
            
            try (OutputStream outputStream = new FileOutputStream(filePath.toFile())) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, bytesRead);
                }
            }
            
            String relativePath = subDir + "/" + datePath + "/" + newFilename;
            String url = baseUrl + "/" + relativePath;
            
            logger.info("[本地存储] 上传成功: {} -> {}", filename, url);
            return url;
            
        } catch (Exception e) {
            logger.error("[本地存储] 上传失败: {}", e.getMessage(), e);
            return null;
        }
    }
    
    public String uploadImage(InputStream inputStream, String filename) {
        return upload(inputStream, filename, "image");
    }
    
    public String uploadVideo(InputStream inputStream, String filename) {
        return upload(inputStream, filename, "video");
    }
    
    public String uploadAudio(InputStream inputStream, String filename) {
        return upload(inputStream, filename, "audio");
    }
    
    public String uploadAvatar(InputStream inputStream, String filename) {
        return upload(inputStream, filename, "avatar");
    }
    
    public String uploadFile(InputStream inputStream, String filename) {
        return upload(inputStream, filename, "file");
    }
    
    public boolean delete(String url) {
        try {
            if (url == null || !url.startsWith(baseUrl)) {
                return false;
            }
            
            String relativePath = url.substring(baseUrl.length() + 1);
            Path filePath = Paths.get(storagePath, relativePath);
            
            if (Files.exists(filePath)) {
                Files.delete(filePath);
                logger.info("[本地存储] 删除成功: {}", filePath);
                return true;
            }
            
            return false;
        } catch (Exception e) {
            logger.error("[本地存储] 删除失败: {}", e.getMessage(), e);
            return false;
        }
    }
    
    private String getSubDir(String filename, String category) {
        if ("avatar".equals(category)) return "avatars";
        if ("video".equals(category)) return "videos";
        if ("audio".equals(category)) return "audios";
        if ("image".equals(category)) return "images";
        
        String extension = getFileExtension(filename).toLowerCase();
        if (extension.matches("\\.(jpg|jpeg|png|gif|webp|bmp|svg)$")) {
            return "images";
        } else if (extension.matches("\\.(mp4|avi|mov|wmv|flv|mkv|webm)$")) {
            return "videos";
        } else if (extension.matches("\\.(mp3|wav|ogg|aac|flac|m4a|amr)$")) {
            return "audios";
        }
        return "files";
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return "";
        }
        int lastDot = filename.lastIndexOf('.');
        if (lastDot > 0) {
            return filename.substring(lastDot);
        }
        return "";
    }
}

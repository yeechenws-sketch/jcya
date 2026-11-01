package com.chat.older.xiechen.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class UploadService {
    
    private static final Logger logger = LoggerFactory.getLogger(UploadService.class);
    
    @Autowired
    private LocalStorageService localStorageService;
    
    @Value("${storage.use-local:true}")
    private boolean useLocalStorage;

    public String upload(InputStream in, String filename) {
        if (useLocalStorage) {
            String url = localStorageService.upload(in, filename, null);
            if (url != null) {
                return url;
            }
            logger.warn("[上传] 本地存储失败，尝试其他方式");
        }
        return null;
    }
    
    public String uploadImage(InputStream in, String filename) {
        if (useLocalStorage) {
            return localStorageService.uploadImage(in, filename);
        }
        return upload(in, filename);
    }
    
    public String uploadVideo(InputStream in, String filename) {
        if (useLocalStorage) {
            return localStorageService.uploadVideo(in, filename);
        }
        return upload(in, filename);
    }
    
    public String uploadAudio(InputStream in, String filename) {
        if (useLocalStorage) {
            return localStorageService.uploadAudio(in, filename);
        }
        return upload(in, filename);
    }
    
    public String uploadAvatar(InputStream in, String filename) {
        if (useLocalStorage) {
            return localStorageService.uploadAvatar(in, filename);
        }
        return upload(in, filename);
    }
}

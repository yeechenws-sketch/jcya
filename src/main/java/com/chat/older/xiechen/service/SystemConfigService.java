package com.chat.older.xiechen.service;

import com.chat.older.xiechen.dao.SystemConfigMapper;
import com.chat.older.xiechen.model.SystemConfig;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
public class SystemConfigService {

    private static final Logger logger = LoggerFactory.getLogger(SystemConfigService.class);

    public static final String REGISTRATION_ENABLED = "registration_enabled";
    public static final String TTS_ENABLED = "tts_enabled";
    public static final String ASR_ENABLED = "asr_enabled";
    public static final String RESOURCES_CONFIG = "resources_config";
    public static final String QINIU_CONFIG = "qiniu_config";
    public static final String CRAWLER_ENABLED = "crawler_enabled";
    public static final String RESUME_CONFIG = "resume_config";
    
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    @Autowired
    private SystemConfigMapper configMapper;

    public boolean isRegistrationEnabled() {
        String value = configMapper.getValueByKey(REGISTRATION_ENABLED);
        return "true".equalsIgnoreCase(value) || value == null;
    }

    public void setRegistrationEnabled(boolean enabled) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(REGISTRATION_ENABLED);
        config.setConfigValue(String.valueOf(enabled));
        config.setDescription("注册功能开关");
        configMapper.insertOrUpdate(config);
    }

    public boolean isTtsEnabled() {
        String value = configMapper.getValueByKey(TTS_ENABLED);
        return "true".equalsIgnoreCase(value) || value == null;
    }

    public void setTtsEnabled(boolean enabled) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(TTS_ENABLED);
        config.setConfigValue(String.valueOf(enabled));
        config.setDescription("百度语音合成(TTS)功能开关");
        configMapper.insertOrUpdate(config);
    }

    public boolean isAsrEnabled() {
        String value = configMapper.getValueByKey(ASR_ENABLED);
        return "true".equalsIgnoreCase(value) || value == null;
    }

    public void setAsrEnabled(boolean enabled) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(ASR_ENABLED);
        config.setConfigValue(String.valueOf(enabled));
        config.setDescription("百度语音识别(ASR)功能开关");
        configMapper.insertOrUpdate(config);
    }

    public String getConfigValue(String key) {
        return configMapper.getValueByKey(key);
    }

    public void setConfigValue(String key, String value, String description) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(key);
        config.setConfigValue(value);
        config.setDescription(description);
        configMapper.insertOrUpdate(config);
    }

    public Map<String, String> getResourcesConfig() {
        String configJson = configMapper.getValueByKey(RESOURCES_CONFIG);
        logger.info("[资源配置] 从数据库读取: {}", configJson);
        if (configJson == null || configJson.isEmpty()) {
            logger.info("[资源配置] 配置为空，返回默认配置");
            Map<String, String> defaultConfig = new HashMap<>();
            defaultConfig.put("heroVideo", "");
            defaultConfig.put("heroImage", "");
            defaultConfig.put("logoNav", "");
            defaultConfig.put("logoFooter", "");
            defaultConfig.put("avatarElder", "");
            defaultConfig.put("avatarFamily", "");
            defaultConfig.put("avatarAdmin", "");
            defaultConfig.put("iconHealth", "");
            defaultConfig.put("iconMedicine", "");
            defaultConfig.put("iconEmergency", "");
            defaultConfig.put("iconVideo", "");
            defaultConfig.put("iconNews", "");
            defaultConfig.put("iconAi", "");
            defaultConfig.put("iconSocial", "");
            defaultConfig.put("iconSystem", "");
            defaultConfig.put("iconNotification", "");
            defaultConfig.put("timelineV1", "");
            defaultConfig.put("timelineV2", "");
            defaultConfig.put("timelineV3", "");
            defaultConfig.put("timelineV4", "");
            defaultConfig.put("timelineFuture", "");
            return defaultConfig;
        }
        
        try {
            Map<String, String> result = objectMapper.readValue(configJson, new TypeReference<Map<String, String>>() {});
            logger.info("[资源配置] 解析成功: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("[资源配置] 解析失败: {}", e.getMessage());
            Map<String, String> defaultConfig = new HashMap<>();
            defaultConfig.put("heroVideo", "");
            defaultConfig.put("heroImage", "");
            return defaultConfig;
        }
    }

    @Transactional
    public void saveResourcesConfig(Map<String, String> config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            logger.info("[资源配置] 准备保存: {}", json);
            SystemConfig sysConfig = new SystemConfig();
            sysConfig.setConfigKey(RESOURCES_CONFIG);
            sysConfig.setConfigValue(json);
            sysConfig.setDescription("About页面资源配置");
            int rows = configMapper.insertOrUpdate(sysConfig);
            logger.info("[资源配置] 保存完成，影响行数: {}", rows);
        } catch (Exception e) {
            logger.error("[资源配置] 保存失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存资源配置失败: " + e.getMessage());
        }
    }

    public Map<String, String> getQiniuConfig() {
        String configJson = configMapper.getValueByKey(QINIU_CONFIG);
        logger.info("[七牛云配置] 从数据库读取: {}", configJson);
        if (configJson == null || configJson.isEmpty()) {
            logger.info("[七牛云配置] 数据库无配置，返回空");
            return new HashMap<>();
        }
        
        try {
            Map<String, String> result = objectMapper.readValue(configJson, new TypeReference<Map<String, String>>() {});
            logger.info("[七牛云配置] 解析成功: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("[七牛云配置] 解析失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    @Transactional
    public void saveQiniuConfig(Map<String, String> config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            logger.info("[七牛云配置] 准备保存: {}", json);
            SystemConfig sysConfig = new SystemConfig();
            sysConfig.setConfigKey(QINIU_CONFIG);
            sysConfig.setConfigValue(json);
            sysConfig.setDescription("七牛云存储配置");
            int rows = configMapper.insertOrUpdate(sysConfig);
            logger.info("[七牛云配置] 保存完成，影响行数: {}", rows);
        } catch (Exception e) {
            logger.error("[七牛云配置] 保存失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存七牛云配置失败: " + e.getMessage());
        }
    }

    public boolean isCrawlerEnabled() {
        String value = configMapper.getValueByKey(CRAWLER_ENABLED);
        return "true".equalsIgnoreCase(value) || value == null;
    }

    public void setCrawlerEnabled(boolean enabled) {
        SystemConfig config = new SystemConfig();
        config.setConfigKey(CRAWLER_ENABLED);
        config.setConfigValue(String.valueOf(enabled));
        config.setDescription("新闻爬虫功能开关");
        configMapper.insertOrUpdate(config);
    }

    public Map<String, String> getResumeConfig() {
        String configJson = configMapper.getValueByKey(RESUME_CONFIG);
        logger.info("[简历配置] 从数据库读取: {}", configJson);
        if (configJson == null || configJson.isEmpty()) {
            logger.info("[简历配置] 配置为空，返回默认配置");
            Map<String, String> defaultConfig = new HashMap<>();
            defaultConfig.put("nickname", "望山遥");
            defaultConfig.put("email", "yeechen177@163.com");
            defaultConfig.put("avatarUrl", "");
            return defaultConfig;
        }
        
        try {
            Map<String, String> result = objectMapper.readValue(configJson, new TypeReference<Map<String, String>>() {});
            logger.info("[简历配置] 解析成功: {}", result);
            return result;
        } catch (Exception e) {
            logger.error("[简历配置] 解析失败: {}", e.getMessage());
            Map<String, String> defaultConfig = new HashMap<>();
            defaultConfig.put("nickname", "望山遥");
            defaultConfig.put("email", "yeechen177@163.com");
            defaultConfig.put("avatarUrl", "");
            return defaultConfig;
        }
    }

    @Transactional
    public void saveResumeConfig(Map<String, String> config) {
        try {
            String json = objectMapper.writeValueAsString(config);
            logger.info("[简历配置] 准备保存: {}", json);
            SystemConfig sysConfig = new SystemConfig();
            sysConfig.setConfigKey(RESUME_CONFIG);
            sysConfig.setConfigValue(json);
            sysConfig.setDescription("简历页面配置（头像和昵称）");
            int rows = configMapper.insertOrUpdate(sysConfig);
            logger.info("[简历配置] 保存完成，影响行数: {}", rows);
        } catch (Exception e) {
            logger.error("[简历配置] 保存失败: {}", e.getMessage(), e);
            throw new RuntimeException("保存简历配置失败: " + e.getMessage());
        }
    }
}

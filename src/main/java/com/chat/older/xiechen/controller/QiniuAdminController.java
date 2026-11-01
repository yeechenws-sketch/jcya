package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.dao.RingtoneConfigMapper;
import com.chat.older.xiechen.model.RingtoneConfig;
import com.chat.older.xiechen.service.SystemConfigService;
import com.chat.older.xiechen.service.UploadService;
import com.qiniu.storage.BucketManager;
import com.qiniu.storage.Configuration;
import com.qiniu.storage.Region;
import com.qiniu.storage.model.FileInfo;
import com.qiniu.util.Auth;
import com.qiniu.util.StringMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/qiniu")
public class QiniuAdminController {

    private static final Logger logger = LoggerFactory.getLogger(QiniuAdminController.class);

    @Value("${qiniu.ak}")
    private String defaultAccessKey;

    @Value("${qiniu.sk}")
    private String defaultSecretKey;

    @Value("${qiniu.bucket}")
    private String defaultBucket;

    @Value("${qiniu.domain:http://t9se9imi4.hn-bkt.clouddn.com/}")
    private String defaultDomain;

    @Autowired
    private UploadService uploadService;

    @Autowired
    private RingtoneConfigMapper ringtoneConfigMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private SystemConfigService systemConfigService;

    private String getAccessKey() {
        Map<String, String> config = systemConfigService.getQiniuConfig();
        String ak = config.get("ak");
        return (ak != null && !ak.isEmpty()) ? ak : defaultAccessKey;
    }

    private String getSecretKey() {
        Map<String, String> config = systemConfigService.getQiniuConfig();
        String sk = config.get("sk");
        return (sk != null && !sk.isEmpty()) ? sk : defaultSecretKey;
    }

    private String getBucket() {
        Map<String, String> config = systemConfigService.getQiniuConfig();
        String bucket = config.get("bucket");
        return (bucket != null && !bucket.isEmpty()) ? bucket : defaultBucket;
    }

    private String getDomain() {
        Map<String, String> config = systemConfigService.getQiniuConfig();
        String domain = config.get("domain");
        return (domain != null && !domain.isEmpty()) ? domain : defaultDomain;
    }

    @PostMapping("/initRingtonesTable")
    public Map<String, Object> initRingtonesTable() {
        Map<String, Object> result = new HashMap<>();
        try {
            jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS `ringtone_config` (" +
                "`id` bigint NOT NULL AUTO_INCREMENT, " +
                "`type` varchar(20) NOT NULL, " +
                "`name` varchar(50) DEFAULT NULL, " +
                "`url` varchar(500) NOT NULL, " +
                "`enabled` tinyint(1) DEFAULT 1, " +
                "`update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "PRIMARY KEY (`id`), " +
                "UNIQUE KEY `uk_type` (`type`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"
            );
            result.put("success", true);
            result.put("message", "铃声配置表创建成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "创建表失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/token")
    public Map<String, Object> getUploadToken() {
        Map<String, Object> result = new HashMap<>();
        try {
            String ak = getAccessKey();
            String sk = getSecretKey();
            String bucket = getBucket();
            String domain = getDomain();
            
            logger.info("[七牛云Token] 使用配置 - ak: {}..., bucket: {}, domain: {}", 
                ak.substring(0, Math.min(8, ak.length())), bucket, domain);
            
            Auth auth = Auth.create(ak, sk);
            String token = auth.uploadToken(bucket, null, 3600, new StringMap()
                .put("insertOnly", 1)
                .put("fsizeLimit", 100 * 1024 * 1024));
            result.put("success", true);
            result.put("token", token);
            result.put("domain", domain);
            result.put("bucket", bucket);
        } catch (Exception e) {
            logger.error("[七牛云Token] 生成失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/config")
    public Map<String, Object> getQiniuConfig() {
        Map<String, Object> result = new HashMap<>();
        try {
            Map<String, String> config = systemConfigService.getQiniuConfig();
            if (config.isEmpty()) {
                config.put("ak", defaultAccessKey);
                config.put("sk", defaultSecretKey);
                config.put("bucket", defaultBucket);
                config.put("domain", defaultDomain);
            }
            result.put("success", true);
            result.put("config", config);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/config")
    public Map<String, Object> saveQiniuConfig(@RequestBody Map<String, String> config) {
        Map<String, Object> result = new HashMap<>();
        try {
            logger.info("[七牛云配置] 保存配置: bucket={}, domain={}", config.get("bucket"), config.get("domain"));
            systemConfigService.saveQiniuConfig(config);
            result.put("success", true);
            result.put("message", "七牛云配置保存成功");
        } catch (Exception e) {
            logger.error("[七牛云配置] 保存失败: {}", e.getMessage());
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/files")
    public Map<String, Object> listFiles(
            @RequestParam(required = false) String prefix,
            @RequestParam(required = false) String marker,
            @RequestParam(defaultValue = "100") int limit) {
        Map<String, Object> result = new HashMap<>();
        try {
            String ak = getAccessKey();
            String sk = getSecretKey();
            String bucket = getBucket();
            String domain = getDomain();
            
            Auth auth = Auth.create(ak, sk);
            Configuration cfg = new Configuration(Region.huanan());
            BucketManager bucketManager = new BucketManager(auth, cfg);

            BucketManager.FileListIterator fileListIterator = bucketManager.createFileListIterator(bucket, prefix == null ? "" : prefix, limit, marker);
            List<Map<String, Object>> items = new ArrayList<>();

            while (fileListIterator.hasNext()) {
                FileInfo[] infos = fileListIterator.next();
                for (FileInfo info : infos) {
                    Map<String, Object> item = new HashMap<>();
                    item.put("key", info.key);
                    item.put("fsize", info.fsize);
                    item.put("hash", info.hash);
                    item.put("mimeType", info.mimeType);
                    item.put("putTime", info.putTime);
                    item.put("type", info.type);
                    items.add(item);
                }
            }

            result.put("success", true);
            result.put("items", items);
            result.put("domain", domain);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/delete")
    public Map<String, Object> deleteFile(@RequestBody Map<String, String> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            String key = params.get("key");
            if (key == null || key.isEmpty()) {
                result.put("success", false);
                result.put("message", "文件key不能为空");
                return result;
            }

            String ak = getAccessKey();
            String sk = getSecretKey();
            String bucket = getBucket();
            
            Auth auth = Auth.create(ak, sk);
            Configuration cfg = new Configuration(Region.huanan());
            BucketManager bucketManager = new BucketManager(auth, cfg);

            bucketManager.delete(bucket, key);
            result.put("success", true);
            result.put("message", "删除成功");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/deleteBatch")
    public Map<String, Object> deleteBatch(@RequestBody Map<String, List<String>> params) {
        Map<String, Object> result = new HashMap<>();
        try {
            List<String> keys = params.get("keys");
            if (keys == null || keys.isEmpty()) {
                result.put("success", false);
                result.put("message", "文件keys不能为空");
                return result;
            }

            String ak = getAccessKey();
            String sk = getSecretKey();
            String bucket = getBucket();
            
            Auth auth = Auth.create(ak, sk);
            Configuration cfg = new Configuration(Region.huanan());
            BucketManager bucketManager = new BucketManager(auth, cfg);

            BucketManager.BatchOperations batchOperations = new BucketManager.BatchOperations();
            batchOperations.addDeleteOp(bucket, keys.toArray(new String[0]));
            bucketManager.batch(batchOperations);

            result.put("success", true);
            result.put("message", "批量删除成功");
            result.put("count", keys.size());
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/stat")
    public Map<String, Object> getBucketStat() {
        Map<String, Object> result = new HashMap<>();
        try {
            String ak = getAccessKey();
            String sk = getSecretKey();
            String bucket = getBucket();
            String domain = getDomain();
            
            Auth auth = Auth.create(ak, sk);
            Configuration cfg = new Configuration(Region.huanan());
            BucketManager bucketManager = new BucketManager(auth, cfg);

            BucketManager.FileListIterator fileListIterator = bucketManager.createFileListIterator(bucket, "", 1000, null);
            long totalSize = 0;
            int fileCount = 0;

            while (fileListIterator.hasNext()) {
                FileInfo[] infos = fileListIterator.next();
                for (FileInfo info : infos) {
                    totalSize += info.fsize;
                    fileCount++;
                }
            }

            result.put("success", true);
            result.put("fileCount", fileCount);
            result.put("totalSize", totalSize);
            result.put("totalSizeMB", totalSize / 1024 / 1024);
            result.put("bucket", bucket);
            result.put("domain", domain);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/test")
    public Map<String, Object> testConnection() {
        Map<String, Object> result = new HashMap<>();
        try {
            String ak = getAccessKey();
            String sk = getSecretKey();
            String bucket = getBucket();
            
            Auth auth = Auth.create(ak, sk);
            Configuration cfg = new Configuration(Region.huanan());
            BucketManager bucketManager = new BucketManager(auth, cfg);

            BucketManager.FileListIterator fileListIterator = bucketManager.createFileListIterator(bucket, "", 1, null);
            boolean hasFiles = fileListIterator.hasNext();

            result.put("success", true);
            result.put("message", "连接成功");
            result.put("bucket", bucket);
            result.put("hasFiles", hasFiles);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", "连接失败: " + e.getMessage());
        }
        return result;
    }

    @GetMapping("/privateUrl")
    public Map<String, Object> getPrivateUrl(@RequestParam String key, @RequestParam(defaultValue = "3600") long expires) {
        Map<String, Object> result = new HashMap<>();
        try {
            String ak = getAccessKey();
            String sk = getSecretKey();
            String domain = getDomain();
            
            Auth auth = Auth.create(ak, sk);
            String url = domain.endsWith("/") ? domain + key : domain + "/" + key;
            String privateUrl = auth.privateDownloadUrl(url, expires);

            result.put("success", true);
            result.put("url", privateUrl);
            result.put("expires", expires);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/ringtones")
    public Map<String, Object> getAllRingtones() {
        Map<String, Object> result = new HashMap<>();
        try {
            List<RingtoneConfig> ringtones = ringtoneConfigMapper.selectAll();
            String domain = getDomain();
            
            Map<String, String> defaultRingtones = new HashMap<>();
            defaultRingtones.put("emergency", domain + "warning.mp3");
            defaultRingtones.put("ring", domain + "ring.mp3");
            defaultRingtones.put("call", domain + "call.mp3");
            defaultRingtones.put("friend", domain + "new%20Friend.wav");
            defaultRingtones.put("message", domain + "notice.wav");

            Map<String, RingtoneConfig> configMap = new HashMap<>();
            for (RingtoneConfig config : ringtones) {
                configMap.put(config.getType(), config);
            }

            List<Map<String, Object>> items = new ArrayList<>();
            for (Map.Entry<String, String> entry : defaultRingtones.entrySet()) {
                Map<String, Object> item = new HashMap<>();
                item.put("type", entry.getKey());
                if (configMap.containsKey(entry.getKey())) {
                    RingtoneConfig config = configMap.get(entry.getKey());
                    item.put("url", config.getUrl());
                    item.put("enabled", config.getEnabled());
                    item.put("custom", true);
                } else {
                    item.put("url", entry.getValue());
                    item.put("enabled", true);
                    item.put("custom", false);
                }
                items.add(item);
            }

            result.put("success", true);
            result.put("items", items);
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @GetMapping("/ringtone/{type}")
    public Map<String, Object> getRingtone(@PathVariable String type) {
        Map<String, Object> result = new HashMap<>();
        try {
            String domain = getDomain();
            RingtoneConfig config = ringtoneConfigMapper.selectByType(type);
            if (config != null) {
                result.put("success", true);
                result.put("url", config.getUrl());
                result.put("enabled", config.getEnabled());
            } else {
                Map<String, String> defaults = new HashMap<>();
                defaults.put("emergency", domain + "warning.mp3");
                defaults.put("ring", domain + "ring.mp3");
                defaults.put("call", domain + "call.mp3");
                defaults.put("friend", domain + "new%20Friend.wav");
                defaults.put("message", domain + "notice.wav");

                result.put("success", true);
                result.put("url", defaults.getOrDefault(type, ""));
                result.put("enabled", true);
            }
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }

    @PostMapping("/ringtone")
    public Map<String, Object> saveRingtone(@RequestBody RingtoneConfig config) {
        Map<String, Object> result = new HashMap<>();
        try {
            if (config.getType() == null || config.getUrl() == null) {
                result.put("success", false);
                result.put("message", "type和url不能为空");
                return result;
            }
            int rows = ringtoneConfigMapper.insertOrUpdate(config);
            result.put("success", true);
            result.put("message", "保存成功");
            result.put("rows", rows);
        } catch (Exception e) {
            e.printStackTrace();
            result.put("success", false);
            result.put("message", "保存失败: " + e.getMessage());
        }
        return result;
    }

    @DeleteMapping("/ringtone/{type}")
    public Map<String, Object> deleteRingtone(@PathVariable String type) {
        Map<String, Object> result = new HashMap<>();
        try {
            ringtoneConfigMapper.deleteByType(type);
            result.put("success", true);
            result.put("message", "已恢复默认铃声");
        } catch (Exception e) {
            result.put("success", false);
            result.put("message", e.getMessage());
        }
        return result;
    }
}

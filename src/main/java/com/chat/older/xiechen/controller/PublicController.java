package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.service.SystemConfigService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/public")
public class PublicController {

    @Autowired
    private SystemConfigService systemConfigService;

    @GetMapping("/resources")
    public ResponseEntity<Map<String, Object>> getResourcesConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("config", systemConfigService.getResourcesConfig());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/resume-config")
    public ResponseEntity<Map<String, Object>> getResumeConfig() {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("config", systemConfigService.getResumeConfig());
        return ResponseEntity.ok(result);
    }
}

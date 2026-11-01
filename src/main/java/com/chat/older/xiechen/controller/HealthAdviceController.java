package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.dao.HealthAdviceMapper;
import com.chat.older.xiechen.model.HealthAdvice;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/health/advice")
public class HealthAdviceController {

    @Autowired
    private HealthAdviceMapper healthAdviceMapper;

    @GetMapping("/list")
    public Result<Map<String, Object>> getAdviceList(
            @RequestParam(required = false) String type,
            @RequestParam(required = false, defaultValue = "5") Integer limit) {
        try {
            List<HealthAdvice> advices;
            
            if (type != null && !type.isEmpty()) {
                advices = healthAdviceMapper.selectByType(type);
            } else if (limit != null && limit > 0) {
                advices = healthAdviceMapper.selectRandom(limit);
            } else {
                advices = healthAdviceMapper.selectAllActive();
            }
            
            List<String> contentList = advices.stream()
                    .map(HealthAdvice::getContent)
                    .collect(Collectors.toList());
            
            Map<String, Object> result = new HashMap<>();
            result.put("advices", advices);
            result.put("contentList", contentList);
            
            return Result.success(result);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取健康建议失败: " + e.getMessage());
        }
    }

    @GetMapping("/contents")
    public Result<List<String>> getAdviceContents(
            @RequestParam(required = false, defaultValue = "5") Integer limit) {
        try {
            List<HealthAdvice> advices = healthAdviceMapper.selectRandom(limit);
            List<String> contentList = advices.stream()
                    .map(HealthAdvice::getContent)
                    .collect(Collectors.toList());
            
            return Result.success(contentList);
        } catch (Exception e) {
            e.printStackTrace();
            return Result.error("获取健康建议失败: " + e.getMessage());
        }
    }
}

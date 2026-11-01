package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.service.HealthAIService;
import com.chat.older.xiechen.service.HealthMonitoringService;
import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.vo.HealthCardVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/health/ai")
public class HealthAIController {

    @Autowired
    private HealthAIService healthAIService;

    @Autowired
    private HealthMonitoringService healthMonitoringService;

    @GetMapping("/summary")
    public Result<String> generateHealthSummary(@RequestParam Long userId) {
        try {
            var healthCards = healthMonitoringService.getHealthCards(userId);
            String summary = healthAIService.generateHealthSummary(userId, healthCards);
            return Result.success(summary);
        } catch (Exception e) {
            return Result.error("生成健康总结失败: " + e.getMessage());
        }
    }

    @GetMapping("/trend")
    public Result<String> analyzeHealthTrend(@RequestParam Long userId, @RequestParam(defaultValue = "7") int days) {
        try {
            String analysis = healthAIService.analyzeHealthTrend(userId, days);
            return Result.success(analysis);
        } catch (Exception e) {
            return Result.error("分析健康趋势失败: " + e.getMessage());
        }
    }

    @GetMapping("/advice")
    public Result<String> generateHealthAdvice(@RequestParam Long userId, @RequestParam String type, @RequestParam float value) {
        try {
            String advice = healthAIService.generateHealthAdvice(userId, type, value);
            return Result.success(advice);
        } catch (Exception e) {
            return Result.error("生成健康建议失败: " + e.getMessage());
        }
    }

    @GetMapping("/compare")
    public Result<String> compareWithPrevious(@RequestParam Long userId, @RequestParam String type, @RequestParam float currentValue) {
        try {
            String comparison = healthAIService.compareWithPrevious(userId, type, currentValue);
            return Result.success(comparison);
        } catch (Exception e) {
            return Result.error("比较健康数据失败: " + e.getMessage());
        }
    }

    @GetMapping("/daily-advices")
    public Result<List<String>> generateDailyHealthAdvices(@RequestParam Long userId) {
        try {
            List<HealthCardVO> healthCards = healthMonitoringService.getHealthCards(userId);
            List<String> advices = healthAIService.generateDailyHealthAdvices(userId, healthCards);
            return Result.success(advices);
        } catch (Exception e) {
            return Result.error("生成每日健康建议失败: " + e.getMessage());
        }
    }
}

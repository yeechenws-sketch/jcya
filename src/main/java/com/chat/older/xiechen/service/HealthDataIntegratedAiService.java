package com.chat.older.xiechen.service;

import com.chat.older.xiechen.vo.HealthCardVO;
import com.chat.older.xiechen.vo.DailyTrendVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class HealthDataIntegratedAiService {

    @Autowired
    private AiAssistant aiAssistant;

    @Autowired
    private HealthMonitoringService healthMonitoringService;

    @Autowired
    private HealthAIService healthAIService;

    public String chatWithHealthData(Long userId, String msg) {
        List<HealthCardVO> currentHealthData = healthMonitoringService.getHealthCards(userId);
        String currentHealthSummary = healthAIService.generateHealthSummary(userId, currentHealthData);
        DailyTrendVO sevenDayTrend = healthMonitoringService.getDailyTrend(userId, 7);
        String sevenDayTrendAnalysis = healthAIService.analyzeHealthTrend(userId, 7);
        String context = String.format("\n【用户健康数据】\n当前健康状况：%s\n最近7天健康趋势：%s\n", 
                currentHealthSummary, sevenDayTrendAnalysis);
        String messageWithContext = msg + context;
        return aiAssistant.chat(userId, messageWithContext);
    }
}

package com.chat.older.xiechen.service;

import com.chat.older.xiechen.vo.HealthCardVO;
import java.util.List;

public interface HealthAIService {
    String generateHealthSummary(Long userId, List<HealthCardVO> healthCards);
    String analyzeHealthTrend(Long userId, int days);
    String generateHealthAdvice(Long userId, String type, float value);
    String compareWithPrevious(Long userId, String type, float currentValue);
    List<String> generateDailyHealthAdvices(Long userId, List<HealthCardVO> healthCards);
}

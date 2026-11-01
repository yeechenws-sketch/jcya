package com.chat.older.xiechen.service;

import com.chat.older.xiechen.model.MqttSimulationConfig;
import java.util.List;
import java.util.Map;

public interface MqttSimulationService {
    Map<String, Object> simulateData(Long userId, String dataType, Float value1, Float value2);
    MqttSimulationConfig saveConfig(MqttSimulationConfig config);
    List<MqttSimulationConfig> getConfigs(Long userId);
    boolean deleteConfig(Long id);
    boolean startAutoSimulation(Long userId);
    boolean stopAutoSimulation(Long userId);
    boolean isSimulationRunning(Long userId);
    Map<String, Object> getSimulationStatus(Long userId);
}

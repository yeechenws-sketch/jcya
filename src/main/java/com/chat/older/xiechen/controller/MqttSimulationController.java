package com.chat.older.xiechen.controller;

import com.chat.older.xiechen.common.Result;
import com.chat.older.xiechen.model.MqttSimulationConfig;
import com.chat.older.xiechen.service.MqttSimulationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/mqtt-simulation")
public class MqttSimulationController {

    @Autowired
    private MqttSimulationService mqttSimulationService;

    @PostMapping("/simulate")
    public Result simulateData(@RequestBody Map<String, Object> params) {
        Long userId = Long.valueOf(params.get("userId").toString());
        String dataType = (String) params.get("dataType");
        Float value1 = params.get("value1") != null ? Float.valueOf(params.get("value1").toString()) : null;
        Float value2 = params.get("value2") != null ? Float.valueOf(params.get("value2").toString()) : null;
        
        Map<String, Object> result = mqttSimulationService.simulateData(userId, dataType, value1, value2);
        if ((Boolean) result.get("success")) {
            return Result.success(result);
        } else {
            return Result.error((String) result.get("message"));
        }
    }

    @PostMapping("/config")
    public Result saveConfig(@RequestBody MqttSimulationConfig config) {
        MqttSimulationConfig saved = mqttSimulationService.saveConfig(config);
        return Result.success(saved);
    }

    @GetMapping("/config/{userId}")
    public Result getConfigs(@PathVariable Long userId) {
        List<MqttSimulationConfig> configs = mqttSimulationService.getConfigs(userId);
        return Result.success(configs);
    }

    @DeleteMapping("/config/{id}")
    public Result deleteConfig(@PathVariable Long id) {
        boolean success = mqttSimulationService.deleteConfig(id);
        return success ? Result.success("删除成功") : Result.error("删除失败");
    }

    @PostMapping("/start/{userId}")
    public Result startSimulation(@PathVariable Long userId) {
        boolean success = mqttSimulationService.startAutoSimulation(userId);
        if (success) {
            return Result.success("自动模拟已启动");
        } else {
            return Result.error("模拟已在运行中");
        }
    }

    @PostMapping("/stop/{userId}")
    public Result stopSimulation(@PathVariable Long userId) {
        boolean success = mqttSimulationService.stopAutoSimulation(userId);
        if (success) {
            return Result.success("自动模拟已停止");
        } else {
            return Result.error("没有正在运行的模拟");
        }
    }

    @GetMapping("/status/{userId}")
    public Result getSimulationStatus(@PathVariable Long userId) {
        Map<String, Object> status = mqttSimulationService.getSimulationStatus(userId);
        return Result.success(status);
    }

    @GetMapping("/is-running/{userId}")
    public Result isSimulationRunning(@PathVariable Long userId) {
        boolean running = mqttSimulationService.isSimulationRunning(userId);
        return Result.success(running);
    }
}

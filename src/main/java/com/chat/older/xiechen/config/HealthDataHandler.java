package com.chat.older.xiechen.config;

import com.chat.older.xiechen.service.MqttMessageHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class HealthDataHandler {

    @Autowired
    private MqttMessageHandler mqttMessageHandler;

    public void process(String message) {
        mqttMessageHandler.handleMessage("health/vitals", message);
    }
}

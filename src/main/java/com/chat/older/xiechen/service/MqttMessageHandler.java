package com.chat.older.xiechen.service;

import com.chat.older.xiechen.service.HealthMonitoringService;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MqttMessageHandler {

    @Autowired
    private HealthMonitoringService healthMonitoringService;

    private com.google.gson.JsonElement getValue(JsonObject jsonObject, String... keys) {
        for (String key : keys) {
            if (jsonObject.has(key)) {
                return jsonObject.get(key);
            }
        }
        throw new IllegalArgumentException("No value found for keys: " + String.join(", ", keys));
    }
    
    public void handleMessage(String topic, String message) {
        System.out.println("Received MQTT message: " + message);
        try {
            JsonObject jsonObject = JsonParser.parseString(message).getAsJsonObject();

            Long userId = jsonObject.get("userId").getAsLong();
            String type = jsonObject.get("type").getAsString();
            String deviceId = jsonObject.get("deviceId").getAsString();
            
            System.out.println("Processing message: userId=" + userId + ", type=" + type + ", deviceId=" + deviceId);

            switch (type) {
                case "BP":
                    float systolic = jsonObject.get("systolic").getAsFloat();
                    float diastolic = jsonObject.get("diastolic").getAsFloat();
                    System.out.println("Processing BP data: systolic=" + systolic + ", diastolic=" + diastolic);
                    healthMonitoringService.processVitalSignsData(userId, type, systolic, diastolic, deviceId);
                    System.out.println("BP data processed successfully");
                    break;
                case "HR":
                    float heartRate = getValue(jsonObject, "value", "heartRate").getAsFloat();
                    System.out.println("Processing HR data: value=" + heartRate);
                    healthMonitoringService.processVitalSignsData(userId, type, heartRate, null, deviceId);
                    System.out.println("HR data processed successfully");
                    break;
                case "BO":
                    float bloodOxygen = getValue(jsonObject, "value", "bloodOxygen").getAsFloat();
                    System.out.println("Processing BO data: value=" + bloodOxygen);
                    healthMonitoringService.processVitalSignsData(userId, type, bloodOxygen, null, deviceId);
                    System.out.println("BO data processed successfully");
                    break;
                case "BG":
                    float bloodGlucose = getValue(jsonObject, "value", "bloodGlucose").getAsFloat();
                    System.out.println("Processing BG data: value=" + bloodGlucose);
                    healthMonitoringService.processVitalSignsData(userId, type, bloodGlucose, null, deviceId);
                    System.out.println("BG data processed successfully");
                    break;
                case "STEPS":
                    int steps = getValue(jsonObject, "value", "steps").getAsInt();
                    System.out.println("Processing STEPS data: value=" + steps);
                    healthMonitoringService.processActivityData(userId, steps, null);
                    System.out.println("STEPS data processed successfully");
                    break;
                case "SLEEP":
                    float sleepHours = getValue(jsonObject, "value", "sleepHours").getAsFloat();
                    System.out.println("Processing SLEEP data: value=" + sleepHours);
                    healthMonitoringService.processActivityData(userId, null, sleepHours);
                    System.out.println("SLEEP data processed successfully");
                    break;
                default:
                    System.out.println("Unknown MQTT message type: " + type);
            }
        } catch (Exception e) {
            System.err.println("Error processing MQTT message: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

package com.chat.older.xiechen.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "livekit")
public class LiveKitConfig {
    private String apiKey;
    private String apiSecret;
    private String host;
    private String wsUrl;
}

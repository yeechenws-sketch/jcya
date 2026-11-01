package com.chat.older.xiechen.config;

import com.chat.older.xiechen.service.*;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class AiConfig {

    @Value("${ai-settings.api-key}")
    private String apiKey;

    @Value("${ai-settings.base-url}")
    private String baseUrl;

    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .maxMessages(10)
                .build();
    }

    @Bean(name = "visionModel")
    public ChatLanguageModel visionModel(@Value("${ai-settings.ocr-model}") String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .logRequests(true)
                .logResponses(true)
                .timeout(Duration.ofSeconds(300))
                .build();
    }

    @Bean(name = "textChatModel")
    public ChatLanguageModel textChatModel(@Value("${ai-settings.chat-model}") String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(300))
                .build();
    }

    @Bean
    public AiAssistant aiAssistant(@Qualifier("textChatModel") ChatLanguageModel model,
                                   ChatMemoryProvider chatMemoryProvider,
                                   AiTools aiTools) {
        return AiServices.builder(AiAssistant.class)
                .chatLanguageModel(model)
                .chatMemoryProvider(chatMemoryProvider)
                .tools(aiTools)
                .build();
    }

    @Bean
    public MedicineExtractor medicineExtractor(@Qualifier("visionModel") ChatLanguageModel model) {
        return AiServices.builder(MedicineExtractor.class)
                .chatLanguageModel(model)
                .build();
    }

    @Bean
    public NewsClassifier newsClassifier(@Qualifier("textChatModel") ChatLanguageModel model) {
        return AiServices.builder(NewsClassifier.class)
                .chatLanguageModel(model)
                .build();
    }

    @Bean
    public NewsContentProcessor contentProcessor(@Qualifier("textChatModel") ChatLanguageModel model) {
        return AiServices.builder(NewsContentProcessor.class)
                .chatLanguageModel(model)
                .build();
    }
}
package com.chat.older.xiechen.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {
    
    @Value("${storage.local.path:/app/uploads}")
    private String storagePath;
    
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + storagePath + "/");
        registry.addResourceHandler("/audio/**")
                .addResourceLocations("file:uploads/audio/");
        registry.addResourceHandler("/news-img/**")
                .addResourceLocations("file:/var/www/news/");
        registry.addResourceHandler("/error/**")
                .addResourceLocations("classpath:/static/error/");
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addRedirectViewController("/", "/about.html");
    }
}
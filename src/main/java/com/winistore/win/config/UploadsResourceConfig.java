package com.winistore.win.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Paths;

@Configuration
public class UploadsResourceConfig implements WebMvcConfigurer {
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String uploadsDir = Paths.get(System.getProperty("user.dir"), "uploads").toString();
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadsDir + "/");
    }
}


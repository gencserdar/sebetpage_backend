package com.serdar.gateway.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves uploaded profile / group photos from the shared Docker volume.
 * user-service writes files; the gateway exposes them publicly at /uploads/**.
 */
@Configuration
public class UploadResourceConfig implements WebMvcConfigurer {

    @Value("${app.upload.storage-dir}")
    private String storageDir;

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = "file:" + storageDir.replace("\\", "/");
        if (!location.endsWith("/")) {
            location += "/";
        }
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations(location)
                .setCachePeriod(3600);
    }
}

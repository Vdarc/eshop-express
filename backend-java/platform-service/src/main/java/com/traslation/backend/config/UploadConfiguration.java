package com.traslation.backend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;

@Configuration
public class UploadConfiguration implements WebMvcConfigurer {
    private final String directory;

    public UploadConfiguration(@Value("${app.upload-dir:../backend/uploads}") String directory) {
        String uri = Path.of(directory).toAbsolutePath().normalize().toUri().toString();
        this.directory = uri.endsWith("/") ? uri : uri + "/";
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**").addResourceLocations(directory);
    }
}

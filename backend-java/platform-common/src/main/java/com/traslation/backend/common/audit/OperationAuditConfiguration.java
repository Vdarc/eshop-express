package com.traslation.backend.common.audit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.traslation.backend.common.id.SnowflakeIdGenerator;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
@ConditionalOnClass(
        name = {
            "org.springframework.web.servlet.DispatcherServlet",
            "org.springframework.jdbc.core.JdbcTemplate"
        })
public class OperationAuditConfiguration implements WebMvcConfigurer {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final SnowflakeIdGenerator idGenerator;

    public OperationAuditConfiguration(
            JdbcTemplate jdbc, ObjectMapper json, SnowflakeIdGenerator idGenerator) {
        this.jdbc = jdbc;
        this.json = json;
        this.idGenerator = idGenerator;
    }

    @Bean
    public OperationAuditRequestFilter operationAuditRequestFilter() {
        return new OperationAuditRequestFilter();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new OperationAuditInterceptor(jdbc, json, idGenerator))
                .addPathPatterns("/api/**");
    }
}

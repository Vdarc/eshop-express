package com.traslation.backend.common.id;

import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.Jackson2ObjectMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the Snowflake generator and makes {@code Long} values safe for browsers.
 *
 * <p>ID columns are {@code bigint} now, and a Snowflake ID is ~19 digits while
 * JavaScript only guarantees exact integers up to 2^53-1. If Jackson writes those
 * values as JSON numbers, {@code JSON.parse} silently rounds them and every
 * subsequent "get by id" request targets a non-existent row. Serialising
 * {@code Long} as a string avoids that without touching any controller code.
 *
 * <p>Only {@code Long}/{@code long} is affected — {@code Integer} counters keep
 * their numeric type, and decimal money fields are untouched.
 */
@Configuration
public class IdGeneratorConfiguration {

    /**
     * Worker id must be unique per running instance, otherwise two services writing in
     * the same millisecond produce identical ids. Defaulting to a fixed value is a trap
     * (every module would pick 1), so derive it from the HTTP port unless explicitly set:
     * 8081→913, 8082→914, ... 10600→360 — distinct for this deployment without any
     * per-service configuration.
     */
    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(
            @Value("${app.id.worker-id:-1}") long configuredWorkerId,
            @Value("${server.port:0}") int serverPort) {
        long workerId = configuredWorkerId >= 0 ? configuredWorkerId : Math.floorMod(serverPort, 1024);
        return new SnowflakeIdGenerator(workerId);
    }

    /**
     * Servlet-only: WebFlux (the gateway) builds its ObjectMapper differently and
     * has no ID fields to serialise.
     */
    @Bean
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public Jackson2ObjectMapperBuilderCustomizer longAsStringCustomizer() {
        return builder ->
                builder.serializerByType(Long.class, ToStringSerializer.instance)
                        .serializerByType(Long.TYPE, ToStringSerializer.instance);
    }
}

package com.suwasarana.api.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Jackson setup.
 *
 * <p>Spring Boot 4 serialises HTTP bodies with <b>Jackson 3</b> ({@code tools.jackson}), whose defaults already write
 * java.time values as ISO-8601 strings ({@code "2026-10-28"}, {@code "2026-10-28T09:30:00Z"}) - see
 * {@code JacksonDatesTest}. The Jackson 2 {@link ObjectMapper} below is only used by code that injects the classic
 * mapper (the Groq/AI clients); it is configured the same way so dates never leak out as numeric arrays.</p>
 */
@Configuration
public class JacksonConfig {

    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        return mapper;
    }
}

package com.jacolp.config;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.jacolp.json.JacksonObjectMapper;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

@Configuration
public class JsonObjectConfiguration {

    /**
     * 配置全局 JSON 序列化器
     */
    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper objectMapper = new ObjectMapper();

        JavaTimeModule javaTimeModule = new JavaTimeModule();
        javaTimeModule.addDeserializer(
                LocalDateTime.class,
                new LocalDateTimeDeserializer(DateTimeFormatter.ofPattern(JacksonObjectMapper.DEFAULT_DATE_TIME_FORMAT)));
        javaTimeModule.addDeserializer(
                LocalDate.class,
                new LocalDateDeserializer(DateTimeFormatter.ofPattern(JacksonObjectMapper.DEFAULT_DATE_FORMAT)));
        javaTimeModule.addDeserializer(
                LocalTime.class,
                new LocalTimeDeserializer(DateTimeFormatter.ofPattern(JacksonObjectMapper.DEFAULT_TIME_FORMAT)));
        javaTimeModule.addSerializer(
                LocalDateTime.class,
                new LocalDateTimeSerializer(DateTimeFormatter.ofPattern(JacksonObjectMapper.DEFAULT_DATE_TIME_FORMAT)));
        javaTimeModule.addSerializer(
                LocalDate.class,
                new LocalDateSerializer(DateTimeFormatter.ofPattern(JacksonObjectMapper.DEFAULT_DATE_FORMAT)));
        javaTimeModule.addSerializer(
                LocalTime.class,
                new LocalTimeSerializer(DateTimeFormatter.ofPattern(JacksonObjectMapper.DEFAULT_TIME_FORMAT)));
        objectMapper.registerModule(javaTimeModule);

        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        objectMapper.setDefaultPropertyInclusion(
                JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.NON_NULL));
        return objectMapper;
    }
}

package com.serdar.chat.config;

import com.serdar.common.AesGcm;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AesConfig {

    @Value("${chat.aes.key}") private String base64Key;

    @Bean
    public AesGcm aesGcm() { return new AesGcm(base64Key); }
}

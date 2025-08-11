package com.serdar.personal.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.util.Base64;

@Configuration
public class AesConfig {

    @Value("${chat.aes.key}") // BASE64 (16/24/32 byte -> AES-128/192/256)
    private String base64Key;

    @Bean
    public SecretKey aesKey() {
        byte[] keyBytes = Base64.getDecoder().decode(base64Key);
        int len = keyBytes.length;
        if (!(len == 16 || len == 24 || len == 32)) {
            throw new IllegalArgumentException("AES key length must be 16/24/32 bytes, got: " + len);
        }
        return new SecretKeySpec(keyBytes, "AES");
    }

    @PostConstruct
    void check() {
        // sadece property var mı kontrol; detay yukarıda
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("chat.aes.key is missing (BASE64)");
        }
    }
}

package com.serdar.user.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class RedisConfig {

    @Bean
    LettuceConnectionFactory userRedisConnectionFactory(
            @Value("${spring.data.redis.host}") String host,
            @Value("${spring.data.redis.port}") int port,
            @Value("${spring.data.redis.password}") String password) {
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
        cfg.setPassword(RedisPassword.of(password));
        return new LettuceConnectionFactory(cfg);
    }

    @Bean
    StringRedisTemplate userStringRedisTemplate(LettuceConnectionFactory userRedisConnectionFactory) {
        return new StringRedisTemplate(userRedisConnectionFactory);
    }
}

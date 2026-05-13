package com.serdar.chat.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "app.chat-events.rabbit-enabled", havingValue = "true")
public class ChatEventRabbitConfig {

    public static final String EXCHANGE = "chat.events.exchange";

    @Bean
    public FanoutExchange chatEventsExchange() {
        return ExchangeBuilder.fanoutExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public Queue chatEventsQueue() {
        return new AnonymousQueue();
    }

    @Bean
    public Binding chatEventsBinding(Queue chatEventsQueue, FanoutExchange chatEventsExchange) {
        return BindingBuilder.bind(chatEventsQueue).to(chatEventsExchange);
    }

    @Bean
    public MessageConverter chatRabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate chatRabbitTemplate(ConnectionFactory cf, MessageConverter chatRabbitMessageConverter) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(chatRabbitMessageConverter);
        return t;
    }
}

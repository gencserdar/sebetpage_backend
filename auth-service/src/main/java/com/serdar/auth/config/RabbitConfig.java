package com.serdar.auth.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Producer-side AMQP wiring.
 *
 * Auth-service only publishes mail jobs — it doesn't consume from any queue.
 * However we declare the exchange + queue + DLX/DLQ here too (idempotent
 * with mail-worker's identical declarations) to defeat a startup-ordering
 * trap: if auth-service comes up before mail-worker has run its
 * RabbitTopology declarations, publishes to a non-existent exchange are
 * silently dropped by RabbitMQ (mandatory=false default). Both sides
 * declaring the same topology means whichever boots first creates it; the
 * second sees the existing definitions and moves on.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE    = "mail.exchange";
    public static final String QUEUE       = "mail.queue";
    public static final String ROUTING_KEY = "send";
    public static final String DLX         = "mail.dlx";
    public static final String DLQ         = "mail.dlq";

    @Bean public DirectExchange mailExchange() { return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build(); }
    @Bean public DirectExchange mailDlx()      { return ExchangeBuilder.directExchange(DLX).durable(true).build(); }

    @Bean
    public Queue mailQueue() {
        return QueueBuilder.durable(QUEUE)
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean public Queue mailDlq() { return QueueBuilder.durable(DLQ).build(); }

    @Bean public Binding mailQueueBinding() { return BindingBuilder.bind(mailQueue()).to(mailExchange()).with(ROUTING_KEY); }
    @Bean public Binding mailDlqBinding()   { return BindingBuilder.bind(mailDlq()).to(mailDlx()).with(ROUTING_KEY); }

    @Bean public MessageConverter rabbitMessageConverter() { return new Jackson2JsonMessageConverter(); }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory cf, MessageConverter conv) {
        RabbitTemplate t = new RabbitTemplate(cf);
        t.setMessageConverter(conv);
        // mandatory=true + return callback would let us KNOW when a publish
        // can't be routed; for now we keep it best-effort to match the
        // previous SMTP-fire-and-forget behavior.
        return t;
    }
}

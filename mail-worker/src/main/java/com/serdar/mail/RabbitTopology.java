package com.serdar.mail;

import org.springframework.amqp.core.*;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares the broker topology this service depends on. Spring AMQP picks
 * these beans up at boot and idempotently creates them on RabbitMQ if they
 * don't already exist — so a fresh `docker compose up` lands a working
 * topology without any manual rabbitmqctl commands.
 *
 * Topology:
 *
 *   mail.exchange (direct)
 *     └── routing key "send" ──> mail.queue
 *                                   │ (on listener failure after retries
 *                                   │  the message is rejected with
 *                                   │  requeue=false; the queue's DLX
 *                                   │  forwards it to mail.dlx with the
 *                                   │  routing key "send".)
 *                                   ▼
 *                                mail.dlx (direct)
 *                                   └── routing key "send" ──> mail.dlq
 *
 * The DLQ is intentionally a plain queue with no consumers — failed jobs
 * sit there for human inspection via the management UI. If you wanted
 * automatic redelivery later, you'd add a TTL + DLX pointing back to
 * mail.exchange.
 */
@Configuration
public class RabbitTopology {

    public static final String EXCHANGE     = "mail.exchange";
    public static final String QUEUE        = "mail.queue";
    public static final String ROUTING_KEY  = "send";
    public static final String DLX          = "mail.dlx";
    public static final String DLQ          = "mail.dlq";

    @Bean
    public DirectExchange mailExchange() {
        return ExchangeBuilder.directExchange(EXCHANGE).durable(true).build();
    }

    @Bean
    public DirectExchange mailDlx() {
        return ExchangeBuilder.directExchange(DLX).durable(true).build();
    }

    @Bean
    public Queue mailQueue() {
        return QueueBuilder.durable(QUEUE)
                // When the listener nacks a delivery (after retries are
                // exhausted) without requeue, RabbitMQ routes it through
                // this DLX/key into mail.dlq.
                .withArgument("x-dead-letter-exchange", DLX)
                .withArgument("x-dead-letter-routing-key", ROUTING_KEY)
                .build();
    }

    @Bean
    public Queue mailDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding mailQueueBinding() {
        return BindingBuilder.bind(mailQueue()).to(mailExchange()).with(ROUTING_KEY);
    }

    @Bean
    public Binding mailDlqBinding() {
        return BindingBuilder.bind(mailDlq()).to(mailDlx()).with(ROUTING_KEY);
    }

    /**
     * JSON over the wire. Producer-side serializes MailJob (or any plain
     * POJO) via Jackson, this converter deserializes on the consumer side.
     * Spring AMQP autoconfig wires this into both RabbitTemplate and
     * SimpleRabbitListenerContainerFactory once it's a bean.
     */
    @Bean
    public MessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }
}

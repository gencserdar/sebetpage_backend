package com.serdar.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Mail entry point for the auth flows. Used to call SMTP directly and swallow
 * failures; now publishes a job to RabbitMQ (mail.exchange / routing key
 * "send") and lets mail-worker handle delivery.
 *
 * Why the switch: SMTP latency was being paid inside @Transactional methods
 * (register, change-email, change-password, etc.), which kept DB transactions
 * open longer than they should be and tied user-facing latency to whatever
 * Gmail was doing. With the broker in front, this call is a tiny in-process
 * publish — the actual send happens out-of-band, retries with exponential
 * backoff, and dead-letters to mail.dlq if the SMTP server stays unreachable.
 *
 * The exchange/routing-key strings are intentionally inline rather than
 * imported from mail-worker — this service has no compile-time dependency on
 * mail-worker, only a wire-format agreement on the JSON shape of the job.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private static final String EXCHANGE    = "mail.exchange";
    private static final String ROUTING_KEY = "send";

    private final RabbitTemplate rabbit;

    public void send(String to, String subject, String body) {
        // LinkedHashMap so the JSON keys come out in a predictable order; the
        // worker's MailJob deserializer doesn't care, but it makes log
        // inspection less surprising.
        Map<String, Object> job = new LinkedHashMap<>();
        job.put("to", to);
        job.put("subject", subject);
        job.put("body", body);

        try {
            rabbit.convertAndSend(EXCHANGE, ROUTING_KEY, job);
            log.debug("Queued mail to {} (subject: {})", to, subject);
        } catch (AmqpException e) {
            // Broker is unreachable. Swallow so the calling auth flow (e.g.
            // register) doesn't roll back its DB write — the user got the
            // account, they just won't get the activation mail right now.
            // This matches the previous "log and move on" behavior of the
            // direct-SMTP version. If you'd rather fail-fast, throw here.
            log.warn("Mail dispatch enqueue failed for {}: {}", to, e.getMessage());
        }
    }
}

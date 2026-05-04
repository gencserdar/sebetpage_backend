package com.serdar.mail;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * Single consumer over mail.queue. Pulls a {@link MailJob}, hands it to
 * SMTP, ack on success.
 *
 * Retry behavior is owned by Spring AMQP's listener container — see
 * {@code spring.rabbitmq.listener.simple.retry.*} in application.yml. After
 * the configured attempts run out and Spring still got an exception, it
 * rejects the message with requeue=false. The queue's DLX (configured in
 * {@link RabbitTopology}) then forwards the dead message into mail.dlq for
 * manual triage via the management UI.
 *
 * If the payload itself is unparseable (e.g. a producer published the wrong
 * shape), there's no point in retrying — we throw
 * {@link AmqpRejectAndDontRequeueException} so Spring skips retries and
 * dead-letters immediately.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MailListener {

    private final JavaMailSender mailSender;

    @RabbitListener(queues = RabbitTopology.QUEUE)
    public void onMail(MailJob job) {
        if (job == null || job.getTo() == null || job.getTo().isBlank()) {
            log.warn("Discarding malformed mail job: {}", job);
            throw new AmqpRejectAndDontRequeueException("Malformed mail job");
        }
        log.info("Sending mail to {} (subject: {})", job.getTo(), job.getSubject());

        SimpleMailMessage msg = new SimpleMailMessage();
        msg.setTo(job.getTo());
        msg.setSubject(job.getSubject() == null ? "" : job.getSubject());
        msg.setText(job.getBody() == null ? "" : job.getBody());
        // Let any MailException bubble up — Spring AMQP's retry interceptor
        // will catch it, retry per the listener config, and ultimately
        // dead-letter if SMTP stays unreachable.
        mailSender.send(msg);
    }
}

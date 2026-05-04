package com.serdar.mail;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Mail worker — listens on the mail.queue and delivers via SMTP.
 *
 * Has no DB, no gRPC, no public HTTP surface. Producers (auth-service,
 * eventually anyone else who needs to send a mail) push a {@link MailJob}
 * onto the queue and forget about it. If SMTP is down, jobs queue up;
 * if a job fails repeatedly, it goes to mail.dlq for inspection.
 */
@SpringBootApplication
public class MailWorkerApplication {
    public static void main(String[] args) {
        SpringApplication.run(MailWorkerApplication.class, args);
    }
}

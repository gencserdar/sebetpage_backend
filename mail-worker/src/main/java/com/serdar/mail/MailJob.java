package com.serdar.mail;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Wire format for a mail job. Kept tiny on purpose — the broker should not
 * carry domain context, only the bare minimum needed to deliver an email.
 *
 * Producers (auth-service today) construct this and publish to mail.exchange.
 * The MailListener in this module reads it and hands it to JavaMailSender.
 *
 * Both sides must agree on this shape. Adding new fields is safe (Jackson
 * ignores unknowns by default); renaming is not — it'd silently drop
 * messages produced by older versions.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MailJob {
    private String to;
    private String subject;
    private String body;
}

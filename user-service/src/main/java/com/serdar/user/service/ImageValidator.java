package com.serdar.user.service;

import com.serdar.common.ServiceException;

import java.util.UUID;

/**
 * Profile photo upload guard. Two jobs:
 *
 *   1. Validate the bytes actually start with a known image-format magic
 *      number — we never trust the client-claimed Content-Type, since a
 *      caller can lie ("image/png" while sending an ELF binary, a script,
 *      etc).
 *   2. Generate a server-controlled relative path so attacker-supplied
 *      filenames (which can contain "../", null bytes, weird unicode)
 *      never end up in our key namespace.
 *
 * The accepted formats are the ones browsers can render in <img>: PNG,
 * JPEG, GIF, WebP. Add SVG only if you've got a sanitizer downstream — raw
 * SVG can carry <script> and is an XSS vector when served from your own
 * CDN domain.
 */
public final class ImageValidator {

    public static final int MAX_BYTES = 5 * 1024 * 1024; // 5 MB

    public record Validated(String canonicalContentType, String safeFilename, byte[] bytes) {}

    private ImageValidator() {}

    public static Validated validate(byte[] bytes, long userId) {
        if (bytes == null || bytes.length == 0)
            throw ServiceException.invalid("Empty file");
        if (bytes.length > MAX_BYTES)
            throw ServiceException.invalid("Image too large (max " + (MAX_BYTES / (1024 * 1024)) + " MB)");

        String contentType = sniff(bytes);
        if (contentType == null)
            throw ServiceException.invalid("File doesn't look like a PNG/JPEG/GIF/WebP image");

        String ext = switch (contentType) {
            case "image/png"  -> "png";
            case "image/jpeg" -> "jpg";
            case "image/gif"  -> "gif";
            case "image/webp" -> "webp";
            default -> "bin"; // unreachable — sniff() returns one of the above or null
        };

        // user-scoped + UUID so two uploads from the same user don't collide
        // and so the original (potentially adversarial) filename never
        // leaves this method.
        String safeName = "u" + userId + "/" + UUID.randomUUID() + "." + ext;
        return new Validated(contentType, safeName, bytes);
    }

    /** Inspect the first bytes for known image-format signatures. Returns
     *  the canonical Content-Type, or null if no match. */
    private static String sniff(byte[] b) {
        if (b.length >= 8
                && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && (b[4] & 0xFF) == 0x0D && (b[5] & 0xFF) == 0x0A
                && (b[6] & 0xFF) == 0x1A && (b[7] & 0xFF) == 0x0A) {
            return "image/png";
        }
        // JPEG: FF D8 FF
        if (b.length >= 3
                && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        // GIF: "GIF87a" or "GIF89a"
        if (b.length >= 6 && b[0] == 'G' && b[1] == 'I' && b[2] == 'F'
                && b[3] == '8' && (b[4] == '7' || b[4] == '9') && b[5] == 'a') {
            return "image/gif";
        }
        // WebP: "RIFF....WEBP"
        if (b.length >= 12
                && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return "image/webp";
        }
        return null;
    }
}

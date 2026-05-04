package com.serdar.common;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM helper with 96-bit IV + 128-bit auth tag.
 * Lifted from the monolith's AesGcmService so chat-service (and anyone else
 * that needs it) can reuse it without duplicating crypto code.
 */
public final class AesGcm {

    private static final int IV_LEN = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RNG = new SecureRandom();

    public record Enc(String ivB64, String cipherB64) {}

    private final SecretKey key;

    public AesGcm(String base64Key) {
        byte[] raw = Base64.getDecoder().decode(base64Key);
        if (raw.length != 32) throw new IllegalArgumentException("AES key must be 32 bytes (256-bit)");
        this.key = new SecretKeySpec(raw, "AES");
    }

    public Enc encrypt(String plaintext, byte[] aad) {
        try {
            byte[] iv = new byte[IV_LEN];
            RNG.nextBytes(iv);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null) c.updateAAD(aad);
            byte[] ct = c.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return new Enc(Base64.getEncoder().encodeToString(iv), Base64.getEncoder().encodeToString(ct));
        } catch (Exception e) {
            throw new RuntimeException("encrypt failed", e);
        }
    }

    public String decrypt(String ivB64, String cipherB64, byte[] aad) {
        try {
            byte[] iv = Base64.getDecoder().decode(ivB64);
            byte[] ct = Base64.getDecoder().decode(cipherB64);
            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            c.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            if (aad != null) c.updateAAD(aad);
            return new String(c.doFinal(ct), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("decrypt failed", e);
        }
    }

    public static byte[] aad(long conversationId, long senderId) {
        return ByteBuffer.allocate(16).putLong(conversationId).putLong(senderId).array();
    }
}

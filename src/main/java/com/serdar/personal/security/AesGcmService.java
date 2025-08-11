// src/main/java/com/serdar/personal/security/AesGcmService.java
package com.serdar.personal.security;

import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class AesGcmService {
    private static final int IV_LEN = 12;       // 96-bit IV (önerilen)
    private static final int TAG_BITS = 128;    // 128-bit auth tag
    private final SecretKey key;
    private final SecureRandom rnd = new SecureRandom();

    public AesGcmService(SecretKey key) {
        this.key = key;
    }

    /** AAD olarak (conversationId, senderId) vb. bağlayabilirsin; null geçilebilir. */
    public Enc encrypt(String plaintext, byte[] aad) {
        try {
            byte[] iv = new byte[IV_LEN];
            rnd.nextBytes(iv);

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_BITS, iv);
            c.init(Cipher.ENCRYPT_MODE, key, spec);
            if (aad != null && aad.length > 0) c.updateAAD(aad);

            byte[] cipher = c.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return new Enc(b64(iv), b64(cipher));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM encrypt failed", e);
        }
    }

    public String decrypt(String ivB64, String cipherB64, byte[] aad) {
        try {
            byte[] iv = b64d(ivB64);
            byte[] cipher = b64d(cipherB64);

            Cipher c = Cipher.getInstance("AES/GCM/NoPadding");
            GCMParameterSpec spec = new GCMParameterSpec(TAG_BITS, iv);
            c.init(Cipher.DECRYPT_MODE, key, spec);
            if (aad != null && aad.length > 0) c.updateAAD(aad);

            byte[] plain = c.doFinal(cipher);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("AES-GCM decrypt failed", e);
        }
    }

    private static String b64(byte[] in) { return Base64.getEncoder().encodeToString(in); }
    private static byte[] b64d(String s) { return Base64.getDecoder().decode(s); }

    @Getter @AllArgsConstructor
    public static class Enc {
        private final String ivB64;
        private final String cipherB64; // ciphertext + auth tag (tek buffer)
    }

    /** Basit AAD helper: convId|senderId gibi bağlama */
    public static byte[] aad(long conversationId, long senderId) {
        ByteBuffer buf = ByteBuffer.allocate(Long.BYTES * 2);
        buf.putLong(conversationId).putLong(senderId);
        return buf.array();
    }
}

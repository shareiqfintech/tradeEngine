package com.example.trading.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * AES-256-GCM encryption for at-rest storage of per-user Groww credentials
 * (see {@link com.example.trading.entity.GrowwConfigurationEntity}). No such
 * mechanism existed in this application before - this is the minimum needed
 * so a user's API key/TOTP secret are never stored in plaintext. Never
 * exposes the plaintext or the key in any exception message.
 */
@Component
public class CredentialEncryptionService {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;

    public CredentialEncryptionService(@Value("${trading.auth.encryption-key}") String base64Key) {
        this.key = new SecretKeySpec(Base64.getDecoder().decode(base64Key), "AES");
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] cipherText = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            ByteBuffer combined = ByteBuffer.allocate(iv.length + cipherText.length);
            combined.put(iv).put(cipherText);
            return Base64.getEncoder().encodeToString(combined.array());
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt credential", ex);
        }
    }

    public String decrypt(String encoded) {
        try {
            byte[] combined = Base64.getDecoder().decode(encoded);
            byte[] iv = Arrays.copyOfRange(combined, 0, IV_LENGTH_BYTES);
            byte[] cipherText = Arrays.copyOfRange(combined, IV_LENGTH_BYTES, combined.length);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(cipherText), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt credential", ex);
        }
    }
}

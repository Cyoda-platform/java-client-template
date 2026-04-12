package com.java_template.common.auth;

// ABOUTME: AES-256-GCM encrypt/decrypt for storing RSA private keys in Cyoda entities.
// Payload format: base64(12-byte IV || GCM ciphertext+tag). No state; all methods static.

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

public class AesGcmEncryption {

    private static final int IV_LENGTH_BYTES = 12;
    private static final int TAG_LENGTH_BITS = 128;
    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private AesGcmEncryption() {}

    public static String encrypt(byte[] plaintext, SecretKey key) {
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext);

            byte[] payload = new byte[IV_LENGTH_BYTES + ciphertext.length];
            System.arraycopy(iv, 0, payload, 0, IV_LENGTH_BYTES);
            System.arraycopy(ciphertext, 0, payload, IV_LENGTH_BYTES, ciphertext.length);

            return Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) {
            throw new OboTokenException("AES-GCM encryption failed", e);
        }
    }

    public static byte[] decrypt(String base64Payload, SecretKey key) {
        try {
            byte[] payload = Base64.getDecoder().decode(base64Payload);
            byte[] iv = Arrays.copyOfRange(payload, 0, IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(payload, IV_LENGTH_BYTES, payload.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_LENGTH_BITS, iv));
            return cipher.doFinal(ciphertext);
        } catch (OboTokenException e) {
            throw e;
        } catch (Exception e) {
            throw new OboTokenException("AES-GCM decryption failed — wrong key or corrupted payload", e);
        }
    }

    public static SecretKey decodeKey(String base64Key) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(base64Key);
            return new SecretKeySpec(keyBytes, "AES");
        } catch (Exception e) {
            throw new OboTokenException("Failed to decode AES encryption key", e);
        }
    }
}

package com.java_template.common.auth;

// ABOUTME: Unit tests for AesGcmEncryption encrypt/decrypt round-trip and error cases.

import org.junit.jupiter.api.Test;

import javax.crypto.SecretKey;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AesGcmEncryptionTest {

    @Test
    void encryptAndDecrypt_roundTrip() {
        SecretKey key = AesGcmEncryption.decodeKey(Base64.getEncoder().encodeToString(new byte[32]));
        byte[] plaintext = "test-private-key-bytes".getBytes();

        String encrypted = AesGcmEncryption.encrypt(plaintext, key);
        byte[] decrypted = AesGcmEncryption.decrypt(encrypted, key);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_producesDifferentCiphertextsForSamePlaintext() {
        SecretKey key = AesGcmEncryption.decodeKey(Base64.getEncoder().encodeToString(new byte[32]));
        byte[] plaintext = "same-input".getBytes();

        String enc1 = AesGcmEncryption.encrypt(plaintext, key);
        String enc2 = AesGcmEncryption.encrypt(plaintext, key);

        assertThat(enc1).isNotEqualTo(enc2);
    }

    @Test
    void decrypt_throwsOboTokenException_onWrongKey() {
        SecretKey key1 = AesGcmEncryption.decodeKey(Base64.getEncoder().encodeToString(new byte[32]));
        byte[] keyBytes = new byte[32];
        keyBytes[0] = 1;
        SecretKey key2 = AesGcmEncryption.decodeKey(Base64.getEncoder().encodeToString(keyBytes));

        String encrypted = AesGcmEncryption.encrypt("secret".getBytes(), key1);

        assertThatThrownBy(() -> AesGcmEncryption.decrypt(encrypted, key2))
                .isInstanceOf(OboTokenException.class);
    }

    @Test
    void decodeKey_throwsOboTokenException_onInvalidBase64() {
        assertThatThrownBy(() -> AesGcmEncryption.decodeKey("not-valid-base64!!!"))
                .isInstanceOf(OboTokenException.class);
    }
}

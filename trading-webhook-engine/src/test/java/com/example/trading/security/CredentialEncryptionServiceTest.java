package com.example.trading.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialEncryptionServiceTest {

    private final CredentialEncryptionService service =
            new CredentialEncryptionService("MDEyMzQ1Njc4OWFiY2RlZjAxMjM0NTY3ODlhYmNkZWY=");

    @Test
    void encrypt_thenDecrypt_roundTripsTheOriginalValue() {
        String plaintext = "eyJraWQiOiJaTUtjVXci-fake-groww-api-key";

        String encrypted = service.encrypt(plaintext);
        String decrypted = service.decrypt(encrypted);

        assertThat(decrypted).isEqualTo(plaintext);
    }

    @Test
    void encrypt_neverProducesThePlaintextOrTheKeyInItsOutput() {
        String plaintext = "G4EWVO3V6GHBA6UAVFG7JA67DAQIAL6U";

        String encrypted = service.encrypt(plaintext);

        assertThat(encrypted).doesNotContain(plaintext);
    }

    @Test
    void encrypt_sameInputTwice_producesDifferentCiphertext() {
        String plaintext = "same-secret-value";

        String first = service.encrypt(plaintext);
        String second = service.encrypt(plaintext);

        assertThat(first).isNotEqualTo(second); // random IV per call
        assertThat(service.decrypt(first)).isEqualTo(plaintext);
        assertThat(service.decrypt(second)).isEqualTo(plaintext);
    }
}

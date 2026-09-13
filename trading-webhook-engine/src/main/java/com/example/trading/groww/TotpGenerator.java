package com.example.trading.groww;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

/**
 * RFC 6238 (TOTP) / RFC 4226 (HOTP) code generator, implemented directly on
 * top of {@code javax.crypto} rather than pulling in a third-party TOTP
 * library, since the JDK already provides everything RFC 6238 needs
 * (HMAC-SHA1 + a 30-second time step + a 6-digit truncation) and this keeps
 * the dependency surface for something security-sensitive as small as
 * possible.
 *
 * <p>Groww's TOTP secret is issued as a Base32 string (the same format every
 * authenticator app - Google Authenticator, Authy, etc. - expects), so
 * decoding it is the other half of this class.
 */
public final class TotpGenerator {

    private static final int TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";

    private TotpGenerator() {
    }

    /** Generates the current 6-digit TOTP code for the given Base32-encoded secret. */
    public static String currentCode(String base32Secret) {
        return generate(base32Secret, Instant.now().getEpochSecond() / TIME_STEP_SECONDS);
    }

    static String generate(String base32Secret, long timeCounter) {
        byte[] key = base32Decode(base32Secret);
        byte[] counterBytes = ByteBuffer.allocate(8).putLong(timeCounter).array();

        byte[] hash = hmacSha1(key, counterBytes);

        int offset = hash[hash.length - 1] & 0x0F;
        int binary = ((hash[offset] & 0x7F) << 24)
                | ((hash[offset + 1] & 0xFF) << 16)
                | ((hash[offset + 2] & 0xFF) << 8)
                | (hash[offset + 3] & 0xFF);

        int otp = binary % (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", otp);
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Unable to compute TOTP HMAC", e);
        }
    }

    /** Minimal RFC 4648 Base32 decoder (no padding required on input). */
    static byte[] base32Decode(String input) {
        String sanitized = input.trim().toUpperCase().replace("=", "");
        if (sanitized.isEmpty()) {
            throw new IllegalArgumentException("TOTP secret must not be empty");
        }

        int bitBuffer = 0;
        int bitsInBuffer = 0;
        byte[] output = new byte[sanitized.length() * 5 / 8];
        int outputIndex = 0;

        for (int i = 0; i < sanitized.length(); i++) {
            char c = sanitized.charAt(i);
            int value = BASE32_ALPHABET.indexOf(c);
            if (value < 0) {
                throw new IllegalArgumentException("Invalid Base32 character in TOTP secret: '" + c + "'");
            }
            bitBuffer = (bitBuffer << 5) | value;
            bitsInBuffer += 5;
            if (bitsInBuffer >= 8) {
                bitsInBuffer -= 8;
                output[outputIndex++] = (byte) ((bitBuffer >> bitsInBuffer) & 0xFF);
            }
        }
        return outputIndex == output.length ? output : java.util.Arrays.copyOf(output, outputIndex);
    }
}

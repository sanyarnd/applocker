package io.github.sanyarnd.applocker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * SHA-1 string encoder, produces 40 lowercase hex characters.
 *
 * @author Alexander Biryukov
 */
final class Sha1Encoder implements LockIdEncoder {
    private static final char[] HEX = "0123456789abcdef".toCharArray();

    @Override
    public String encode(final String inputString) {
        final MessageDigest sha1;
        try {
            sha1 = MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException ex) {
            // every Java platform implementation is required to support SHA-1
            throw new AssertionError(ex);
        }

        final byte[] bytes = sha1.digest(inputString.getBytes(StandardCharsets.UTF_8));
        final char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; ++i) {
            chars[2 * i] = HEX[(bytes[i] >> 4) & 0xF];
            chars[2 * i + 1] = HEX[bytes[i] & 0xF];
        }
        return new String(chars);
    }
}

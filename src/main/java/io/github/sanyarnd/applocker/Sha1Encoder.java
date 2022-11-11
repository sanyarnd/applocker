package io.github.sanyarnd.applocker;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import org.jetbrains.annotations.NotNull;

/**
 * SHA-1 string encoder.
 *
 * @author Alexander Biryukov
 */
final class Sha1Encoder implements LockIdEncoder {
    @Override
    public @NotNull String encode(final @NotNull String inputString) {
        try {
            final MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
            sha1.reset();
            sha1.update(inputString.getBytes(StandardCharsets.UTF_8));

            final byte[] bytes = sha1.digest();
            try (Formatter formatter = new Formatter()) {
                for (byte b : bytes) {
                    formatter.format("%02x", b);
                }
                return formatter.toString();
            }
        } catch (NoSuchAlgorithmException ex) {
            /* not happening */
            throw new AssertionError(ex);
        }
    }
}

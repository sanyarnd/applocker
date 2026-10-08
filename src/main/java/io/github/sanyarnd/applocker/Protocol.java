package io.github.sanyarnd.applocker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

/// Wire format of the messages: the client sends `token, length, UTF-8 bytes`, the server answers with
/// `length, UTF-8 bytes`.
final class Protocol {
    static final int TOKEN_BYTES = 32;
    static final int MAX_MESSAGE_BYTES = 1 << 20;

    private static final SecureRandom RANDOM = new SecureRandom();

    private Protocol() {}

    static byte[] newToken() {
        final byte[] token = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(token);
        return token;
    }

    static void writeMessage(final DataOutputStream out, final String message) throws IOException {
        final byte[] bytes = message.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_MESSAGE_BYTES) {
            throw new LockingException("Message exceeds " + MAX_MESSAGE_BYTES + " bytes");
        }
        out.writeInt(bytes.length);
        out.write(bytes);
        out.flush();
    }

    static String readMessage(final DataInputStream in) throws IOException {
        final int length = in.readInt();
        if (length < 0 || length > MAX_MESSAGE_BYTES) {
            throw new IOException("Invalid message length " + length);
        }
        final byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, StandardCharsets.UTF_8);
    }
}

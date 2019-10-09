package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;

/**
 * Exception indicates that there is an issue with message server.
 *
 * @author Alexander Biryukov
 */
public class LockingMessageServerException extends LockingException {
    LockingMessageServerException(final @NotNull String message) {
        super(message);
    }
}

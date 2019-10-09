package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;

/**
 * Exception indicates that there is an issue with message server.
 *
 * @author Alexander Biryukov
 */
public class LockingMessageServerException extends LockingException {
    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()}
     *                method.
     */
    public LockingMessageServerException(final @NotNull String message) {
        super(message);
    }
}

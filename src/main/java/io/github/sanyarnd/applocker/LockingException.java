package io.github.sanyarnd.applocker;

import org.jspecify.annotations.Nullable;

/**
 * The Exception indicates that there was a failure (most probably I/O) during acquiring the lock.
 *
 * @author Alexander Biryukov
 */
public class LockingException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    LockingException(final @Nullable String message, final @Nullable Throwable cause) {
        super(message, cause);
    }

    LockingException(final @Nullable Throwable cause) {
        super(cause);
    }

    LockingException(final @Nullable String message) {
        super(message);
    }
}

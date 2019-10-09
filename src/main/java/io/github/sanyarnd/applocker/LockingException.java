package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Superclass of all locking exceptions.
 *
 * @author Alexander Biryukov
 */
public class LockingException extends RuntimeException {
    LockingException(final @Nullable String message, final @NotNull Throwable cause) {
        super(message, cause);
    }

    LockingException(final @NotNull Throwable cause) {
        super(cause);
    }

    LockingException(final @NotNull String message) {
        super(message);
    }
}

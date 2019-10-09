package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception indicates that there was a failure (most probably I/O) during acquiring the lock.
 *
 * @author Alexander Biryukov
 */

public class LockingFailedException extends LockingException {
    LockingFailedException(final @Nullable String message, final @NotNull Throwable cause) {
        super(message, cause);
    }
}

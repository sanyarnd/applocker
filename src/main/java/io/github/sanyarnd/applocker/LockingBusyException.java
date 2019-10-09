package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;

/**
 * Exception indicates that the lock has already been acquired.
 *
 * @author Alexander Biryukov
 */
public class LockingBusyException extends LockingException {
    LockingBusyException(final @NotNull Throwable cause) {
        super(cause);
    }
}

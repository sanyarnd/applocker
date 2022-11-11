package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.Nullable;

/**
 * Exception indicates that the lock has already been acquired.
 *
 * @author Alexander Biryukov
 */
public class LockingBusyException extends LockingException {
    /**
     * Create lock busy exception
     *
     * @param message exception message
     * @param cause   exception cause
     */
    public LockingBusyException(@Nullable final String message, @Nullable final Throwable cause) {
        super(message, cause);
    }
}

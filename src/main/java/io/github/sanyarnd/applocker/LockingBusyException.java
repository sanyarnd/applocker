package io.github.sanyarnd.applocker;

import org.jspecify.annotations.Nullable;

/// Exception indicates that the lock has already been acquired.
///
/// @author Alexander Biryukov
public class LockingBusyException extends LockingException {
    private static final long serialVersionUID = 1L;

    LockingBusyException(final @Nullable String message, final @Nullable Throwable cause) {
        super(message, cause);
    }
}

package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Exception indicates that there is a problem with communication between current instance and lock holder.
 *
 * @author Alexander Biryukov
 */
public class LockingCommunicationException extends LockingException {
    LockingCommunicationException(final @Nullable String message, final @NotNull Throwable cause) {
        super(message, cause);
    }

    LockingCommunicationException(final @NotNull String message) {
        super(message);
    }
}

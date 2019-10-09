package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;

/**
 * Exception indicates that there was a failure (most probably I/O) during acquiring the lock.
 *
 * @author Alexander Biryukov
 */
public class LockingFailedException extends LockingException {
    /**
     * Constructs a new exception with the specified cause and a detail message of <tt>(cause==null ? null :
     * cause.toString())</tt> (which typically contains the class and detail message of
     * <tt>cause</tt>).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).  (A <tt>null</tt>
     *              value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public LockingFailedException(final @NotNull Throwable cause) {
        super(cause);
    }
}

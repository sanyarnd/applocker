package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;

/**
 * Superclass of all locking exceptions.
 *
 * @author Alexander Biryukov
 */
public class LockingException extends RuntimeException {
    /**
     * Constructs a new exception with the specified cause and a detail message of <tt>(cause==null ? null :
     * cause.toString())</tt> (which typically contains the class and detail message of
     * <tt>cause</tt>).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).  (A <tt>null</tt>
     *              value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public LockingException(final @NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()}
     *                method.
     */
    public LockingException(final @NotNull String message) {
        super(message);
    }
}

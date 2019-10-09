package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;

/**
 * Exception indicates that there is a problem with communication between current instance and lock holder.
 *
 * @author Alexander Biryukov
 */
public class LockingCommunicationException extends LockingException {
    /**
     * Constructs a new exception with the specified cause and a detail message of <tt>(cause==null ? null :
     * cause.toString())</tt> (which typically contains the class and detail message of
     * <tt>cause</tt>).
     *
     * @param cause the cause (which is saved for later retrieval by the {@link #getCause()} method).  (A <tt>null</tt>
     *              value is permitted, and indicates that the cause is nonexistent or unknown.)
     */
    public LockingCommunicationException(final @NotNull Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new exception with the specified detail message.
     *
     * @param message the detail message. The detail message is saved for later retrieval by the {@link #getMessage()}
     *                method.
     */
    public LockingCommunicationException(final @NotNull String message) {
        super(message);
    }
}

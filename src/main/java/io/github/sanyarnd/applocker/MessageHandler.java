package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

/**
 * Interface for the function that runs on the server side and handles all incoming messages.
 *
 * @param <I> message type
 * @param <O> answer type
 */
@FunctionalInterface
public interface MessageHandler<I extends Serializable, O extends Serializable> {
    /**
     * Handle the received message and return the result.
     *
     * @param message input message
     * @return result of the message processing
     */
    @NotNull
    O handleMessage(@NotNull I message);
}

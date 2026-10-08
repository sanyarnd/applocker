package io.github.sanyarnd.applocker;

/// Function that runs on the lock owner side and handles all incoming messages.
@FunctionalInterface
public interface MessageHandler {
    /// Handle the received message and return the answer.
    ///
    /// @param message input message
    /// @return answer to the sender
    String handleMessage(String message);
}

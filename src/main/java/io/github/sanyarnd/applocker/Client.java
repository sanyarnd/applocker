package io.github.sanyarnd.applocker;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;

/**
 * Client who can communicate with {@link Server} object.
 *
 * @param <I> send message type
 * @param <O> receive message type
 * @author Alexander Biryukov
 */
@Slf4j
final class Client<I extends Serializable, O extends Serializable> {
    private final int port;

    Client(final int portNumber) {
        port = portNumber;
    }

    @NotNull
    O send(final @NotNull I message) {
        log.debug("Sending message to localhost:{}", port);
        try (Socket socket = new Socket(InetAddress.getLocalHost(), port);
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            socket.setReuseAddress(true);
            output.writeObject(message);

            return (O) input.readObject();
        } catch (ClassNotFoundException ex) {
            log.debug("Cannot deserialize answer, no such class");
            throw new LockingCommunicationException("Unable to deserialize the message", ex);
        } catch (ConnectException ex) {
            log.debug("Unable to connect to localhost:{}", port);
            throw new LockingCommunicationException("Unable to connect to the message server", ex);
        } catch (IOException ex) {
            log.debug("Some I/O error");
            throw new LockingCommunicationException("I/O commutation error", ex);
        }
    }
}

package io.github.sanyarnd.applocker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.Socket;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Client who can communicate with {@link Server} object.
 *
 * @param <I> send message type
 * @param <O> receive message type
 * @author Alexander Biryukov
 */
final class Client<I extends Serializable, O extends Serializable> {
    private static final Logger LOG = LoggerFactory.getLogger(Client.class);

    private final int port;

    Client(final int portNumber) {
        port = portNumber;
    }

    @SuppressWarnings("unchecked")
    @NotNull O send(final @NotNull I message) {
        LOG.debug("Sending message to localhost:{}", port);
        try (Socket socket = new Socket(InetAddress.getLocalHost(), port);
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            socket.setReuseAddress(true);
            output.writeObject(message);

            return (O) input.readObject();
        } catch (ClassNotFoundException ex) {
            LOG.debug("Cannot deserialize answer, no such class");
            throw new LockingException("Unable to deserialize the message", ex);
        } catch (ConnectException ex) {
            LOG.debug("Unable to connect to localhost:{}", port);
            throw new LockingException("Unable to connect to the message server", ex);
        } catch (IOException ex) {
            LOG.debug("Some I/O error");
            throw new LockingException("I/O commutation error", ex);
        }
    }
}

package io.github.sanyarnd.applocker;

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
final class Client<I extends Serializable, O extends Serializable> {
    private final int port;

    Client(final int portNumber) {
        port = portNumber;
    }

    @NotNull
    O send(final @NotNull I message) {
        try (Socket socket = new Socket(InetAddress.getLocalHost(), port);
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            socket.setReuseAddress(true);
            output.writeObject(message);

            final O ret = (O) input.readObject();

            return ret;
        } catch (ClassNotFoundException ex) {
            throw new LockingCommunicationException("Unable to deserialize the message");
        } catch (ConnectException ex) {
            throw new LockingCommunicationException("Unable to connect to the message server");
        } catch (IOException ex) {
            throw new LockingCommunicationException(ex);
        }
    }
}

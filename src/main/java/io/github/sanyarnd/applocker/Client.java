package io.github.sanyarnd.applocker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/**
 * Client who can communicate with {@link Server} object over the loopback interface.
 *
 * @param <I> send message type
 * @param <O> receive message type
 * @author Alexander Biryukov
 */
final class Client<I extends Serializable, O extends Serializable> {
    private static final Logger LOG = System.getLogger(Client.class.getName());
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    // TODO: make configurable
    private static final int DEFAULT_READ_TIMEOUT_MS = 30_000;

    private final int port;
    private final int readTimeoutMs;

    Client(final int portNumber) {
        this(portNumber, DEFAULT_READ_TIMEOUT_MS);
    }

    Client(final int portNumber, final int readTimeout) {
        port = portNumber;
        readTimeoutMs = readTimeout;
    }

    @SuppressWarnings("unchecked")
    O send(final I message) {
        LOG.log(Level.DEBUG, "Sending message to localhost:{0}", port);
        try (Socket socket = connect();
                ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {
            // ObjectInputStream constructor blocks until the peer sends its stream header
            output.flush();
            try (ObjectInputStream input = new ObjectInputStream(socket.getInputStream())) {
                output.writeObject(message);
                output.flush();
                return (O) input.readObject();
            }
        } catch (SocketTimeoutException ex) {
            LOG.log(Level.DEBUG, "Timeout during communication with localhost:{0}", port);
            throw new LockingException("Message server did not answer in time", ex);
        } catch (ClassNotFoundException ex) {
            LOG.log(Level.DEBUG, "Cannot deserialize answer, no such class", ex);
            throw new LockingException("Unable to deserialize the message", ex);
        } catch (ConnectException ex) {
            LOG.log(Level.DEBUG, "Unable to connect to localhost:{0}", port);
            throw new LockingException("Unable to connect to the message server", ex);
        } catch (IOException ex) {
            LOG.log(Level.DEBUG, "I/O error during communication with the message server", ex);
            throw new LockingException("I/O communication error", ex);
        }
    }

    private Socket connect() throws IOException {
        final Socket socket = new Socket();
        try {
            socket.setSoTimeout(readTimeoutMs);
            socket.connect(new InetSocketAddress(InetAddress.getLoopbackAddress(), port), CONNECT_TIMEOUT_MS);
            return socket;
        } catch (IOException ex) {
            socket.close();
            throw ex;
        }
    }
}

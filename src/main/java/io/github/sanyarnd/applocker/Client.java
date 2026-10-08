package io.github.sanyarnd.applocker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;

/// Client who can communicate with [Server] object over the loopback interface.
///
/// @author Alexander Biryukov
final class Client {
    private static final Logger LOG = System.getLogger(Client.class.getName());
    private static final int CONNECT_TIMEOUT_MS = 5_000;

    private final int port;
    private final byte[] token;
    private final int readTimeoutMs;

    Client(final int portNumber, final byte[] serverToken, final int readTimeout) {
        port = portNumber;
        token = serverToken.clone();
        readTimeoutMs = readTimeout;
    }

    String send(final String message) {
        LOG.log(Level.DEBUG, "Sending message to localhost:{0}", port);
        try (Socket socket = connect();
                DataOutputStream output = new DataOutputStream(socket.getOutputStream());
                DataInputStream input = new DataInputStream(socket.getInputStream())) {
            output.write(token);
            Protocol.writeMessage(output, message);
            return Protocol.readMessage(input);
        } catch (SocketTimeoutException ex) {
            LOG.log(Level.DEBUG, "Timeout during communication with localhost:{0}", port);
            throw new LockingException("Message server did not answer in time", ex);
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

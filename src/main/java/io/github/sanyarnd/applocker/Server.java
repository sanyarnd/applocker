package io.github.sanyarnd.applocker;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.security.MessageDigest;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;

/// Socket-based server, accepts connections on the loopback interface only.
///
/// @author Alexander Biryukov
final class Server implements AutoCloseable {
    private static final Logger LOG = System.getLogger(Server.class.getName());
    private static final int REQUEST_TIMEOUT_MS = 5_000;

    private final MessageHandler messageHandler;
    private final ExecutorService executor;
    private @Nullable ServerSocketChannel socket;
    private @Nullable Future<?> loop;
    private int port = -1;

    Server(final MessageHandler handler) {
        messageHandler = handler;
        executor = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "AppLocker MessageServer");
            t.setDaemon(true);
            return t;
        });
    }

    /// Open the server socket and start accepting connections in background.
    ///
    /// @param clientToken token every client must send before the message
    /// @return server port
    /// @throws LockingException if the server is already running or the socket cannot be opened
    synchronized int start(final byte[] clientToken) {
        if (socket != null) {
            throw new LockingException("The server is already running");
        }

        final ServerSocketChannel channel;
        try {
            channel = ServerSocketChannel.open();
        } catch (IOException ex) {
            throw new LockingException("Unable to open message server socket", ex);
        }
        try {
            channel.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
            port = ((InetSocketAddress) channel.getLocalAddress()).getPort();
        } catch (IOException ex) {
            closeQuietly(channel);
            throw new LockingException("Unable to open message server socket", ex);
        }

        socket = channel;
        final byte[] expectedToken = clientToken.clone();
        loop = executor.submit(() -> acceptLoop(channel, expectedToken));
        LOG.log(Level.DEBUG, "Started message server on localhost:{0}", port);
        return port;
    }

    @Override
    public void close() {
        stop();
        executor.shutdown();
    }

    synchronized void stop() {
        if (socket == null) {
            return;
        }

        closeQuietly(socket);
        if (loop != null) {
            loop.cancel(true);
        }
        socket = null;
        loop = null;
        port = -1;
        LOG.log(Level.DEBUG, "Message server stopped");
    }

    /// Get server's socket port.
    ///
    /// @return port
    /// @throws LockingException if the server is not running
    synchronized int getPort() {
        if (socket == null) {
            throw new LockingException("Message server is not running");
        }
        return port;
    }

    private void acceptLoop(final ServerSocketChannel channel, final byte[] expectedToken) {
        try {
            while (true) {
                handleConnection(channel.accept(), expectedToken);
            }
        } catch (ClosedChannelException ex) {
            LOG.log(Level.DEBUG, "Message server socket closed");
        } catch (IOException ex) {
            LOG.log(Level.ERROR, "Message server socket failure", ex);
            closeQuietly(channel);
        }
    }

    private void handleConnection(final SocketChannel channel, final byte[] expectedToken) {
        try (SocketChannel ch = channel;
                Socket connSocket = withTimeout(ch.socket());
                DataInputStream input = new DataInputStream(connSocket.getInputStream());
                DataOutputStream output = new DataOutputStream(connSocket.getOutputStream())) {
            LOG.log(Level.DEBUG, "New connection from localhost:{0}", connSocket.getPort());

            final byte[] clientToken = new byte[Protocol.TOKEN_BYTES];
            input.readFully(clientToken);
            if (!MessageDigest.isEqual(clientToken, expectedToken)) {
                LOG.log(Level.WARNING, "Rejected connection with invalid token");
                return;
            }

            final String message = Protocol.readMessage(input);
            final String response;
            try {
                response = messageHandler.handleMessage(message);
            } catch (RuntimeException ex) {
                // the client will get an EOF and report the failure on its side
                LOG.log(Level.ERROR, () -> "Error during processing message " + message, ex);
                return;
            }
            Protocol.writeMessage(output, response);
        } catch (IOException | LockingException ex) {
            LOG.log(Level.WARNING, "Unable to process incoming message", ex);
        }
    }

    private static Socket withTimeout(final Socket socket) throws IOException {
        socket.setSoTimeout(REQUEST_TIMEOUT_MS);
        return socket;
    }

    private static void closeQuietly(final ServerSocketChannel channel) {
        try {
            channel.close();
        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Unable to close message server socket", ex);
        }
    }
}

package io.github.sanyarnd.applocker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jspecify.annotations.Nullable;

/**
 * Socket-based server, accepts connections on the loopback interface only.
 *
 * @param <I> receive message type
 * @param <O> response message type
 * @author Alexander Biryukov
 */
final class Server<I extends Serializable, O extends Serializable> implements AutoCloseable {
    private static final Logger LOG = System.getLogger(Server.class.getName());
    private static final int REQUEST_TIMEOUT_MS = 5_000;

    private final MessageHandler<I, O> messageHandler;
    private final ExecutorService executor;
    private @Nullable ServerSocketChannel socket;
    private @Nullable Future<?> loop;
    private int port = -1;

    Server(final MessageHandler<I, O> handler) {
        messageHandler = handler;
        executor = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "AppLocker MessageServer");
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Open the server socket and start accepting connections in background.
     *
     * @return server port
     * @throws LockingException if the server is already running or the socket cannot be opened
     */
    synchronized int start() {
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
        loop = executor.submit(() -> acceptLoop(channel));
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

    /**
     * Get server's socket port.
     *
     * @return port
     * @throws LockingException if the server is not running
     */
    synchronized int getPort() {
        if (socket == null) {
            throw new LockingException("Message server is not running");
        }
        return port;
    }

    private void acceptLoop(final ServerSocketChannel channel) {
        try {
            while (true) {
                handleConnection(channel.accept());
            }
        } catch (ClosedChannelException ex) {
            LOG.log(Level.DEBUG, "Message server socket closed");
        } catch (IOException ex) {
            LOG.log(Level.ERROR, "Message server socket failure", ex);
            closeQuietly(channel);
        }
    }

    @SuppressWarnings("unchecked")
    private void handleConnection(final SocketChannel channel) {
        try (SocketChannel ch = channel;
                Socket connSocket = withTimeout(ch.socket());
                ObjectOutputStream oos = new ObjectOutputStream(connSocket.getOutputStream());
                ObjectInputStream ois = new ObjectInputStream(connSocket.getInputStream())) {
            LOG.log(Level.DEBUG, "New connection from localhost:{0}", connSocket.getPort());

            final I message = (I) ois.readObject();
            LOG.log(Level.DEBUG, "Incoming message: {0}", message);
            final O response;
            try {
                response = messageHandler.handleMessage(message);
            } catch (RuntimeException ex) {
                // the client will get an EOF and report the failure on its side
                LOG.log(Level.ERROR, () -> "Error during processing message " + message, ex);
                return;
            }
            LOG.log(Level.DEBUG, "Calculated response: {0}", response);
            oos.writeObject(response);
        } catch (IOException | ClassNotFoundException ex) {
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

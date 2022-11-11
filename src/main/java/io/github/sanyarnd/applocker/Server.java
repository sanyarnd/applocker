package io.github.sanyarnd.applocker;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import static java.lang.String.format;

/**
 * Socket-based server.
 *
 * @param <I> receive message type
 * @param <O> response message type
 * @author Alexander Biryukov
 */
final class Server<I extends Serializable, O extends Serializable> implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(Server.class);
    private static final int PORT_SLEEP_TIMEOUT_MS = 10;

    private final @NotNull MessageHandler<I, O> messageHandler;
    private final @NotNull ExecutorService executor;
    private @Nullable Future<?> threadHandle;
    private @Nullable ServerLoop runnable;

    Server(final @NotNull MessageHandler<I, O> handler) {
        messageHandler = handler;
        executor = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "AppLocker MessageServer");
            t.setDaemon(true);
            return t;
        });
    }

    void start() {
        LOG.debug("Init message server");
        if (threadHandle != null) {
            throw new LockingException("The server is already running");
        }

        runnable = new ServerLoop();
        threadHandle = executor.submit(runnable);

        LOG.debug("Message server initialized");
    }

    @Override
    public void close() {
        stop();
        executor.shutdown();
    }

    public void stop() {
        LOG.debug("Stopping message server");

        if (threadHandle != null) {
            threadHandle.cancel(true);
        }

        threadHandle = null;
        runnable = null;
        LOG.debug("Message server stopped");
    }

    /**
     * Get server's socket port.
     *
     * @return port
     * @throws LockingException if a message server is not running or server is in exception state
     */
    int tryGetPort() {
        LOG.debug("Requesting server port number");
        if (threadHandle != null && threadHandle.isDone()) {
            throw new LockingException("Server is in exception state for some reason");
        }
        if (runnable == null || runnable.port == -1) {
            throw new LockingException("Message server is not running");
        }
        LOG.debug("Retrieved server port number: {}", runnable.port);
        return runnable.port;
    }

    /**
     * Blocking version of {@link #tryGetPort()} ()}, ignores {@link LockingException} and tries to retrieve the
     * port number.
     * This method is useful for situations where you need to retrieve the port number right after the start
     *
     * @param timeoutMs timeout in milliseconds
     * @return port number
     * @throws LockingException if a message server is not running or server is in exception state
     */
    int getPort(final long timeoutMs) throws InterruptedException {
        final long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - timeoutMs <= start) {
            try {
                return tryGetPort();
            } catch (LockingException ignored) {
                Thread.sleep(PORT_SLEEP_TIMEOUT_MS);
            }
        }
        throw new LockingException(format("Lock attempt timeout=%dms exceeded", timeoutMs));
    }

    final class ServerLoop implements Runnable {
        private volatile int port = -1;

        @Override
        public void run() {
            LOG.debug("Opening message server port");
            // use a socket channel, because it'll throw ClosedByInterruptException on interrupt
            try (ServerSocketChannel socket = ServerSocketChannel.open();
                 ServerSocket realSocket = socket.socket()) {
                realSocket.setReuseAddress(true);
                realSocket.bind(new InetSocketAddress(0));
                port = realSocket.getLocalPort();
                socket.configureBlocking(true);

                LOG.info("Staring message server on localhost:{}", port);

                while (!Thread.currentThread().isInterrupted()) {
                    run0(socket);
                }
            } catch (IOException ex) {
                // something wrong happened with socket
                LOG.error("Cannot initialize the socket", ex);
                throw new RuntimeException(ex);
            }
        }

        private void run0(final ServerSocketChannel socket) throws IOException {
            try (SocketChannel channel = socket.accept();
                 Socket connSocket = channel.socket();
                 ObjectOutputStream oos = new ObjectOutputStream(connSocket.getOutputStream());
                 ObjectInputStream ois = new ObjectInputStream(connSocket.getInputStream())) {
                LOG.debug("New connection from localhost:{}", connSocket.getPort());
                try {
                    @SuppressWarnings("unchecked") final I message = (I) ois.readObject();
                    LOG.debug("Incoming message: {}", message);
                    try {
                        final O response = messageHandler.handleMessage(message);
                        LOG.debug("Calculated response: {}", response);
                        oos.writeObject(response);
                    } catch (RuntimeException ex) {
                        LOG.error("Error during processing message {}", message, ex);
                    }
                } catch (IOException | ClassNotFoundException ex) {
                    // there's a failure during de-serialization or handling the message,
                    // but we don't want to terminate the server
                    LOG.error("Error during deserialization", ex);
                }
            }
        }
    }
}

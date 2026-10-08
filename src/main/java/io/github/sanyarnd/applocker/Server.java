package io.github.sanyarnd.applocker;

import static java.lang.String.format;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
    private static final long PORT_SLEEP_TIMEOUT_MS = 10;
    // a client which doesn't send its request in time must not block the server forever
    private static final int REQUEST_TIMEOUT_MS = 5_000;

    private final MessageHandler<I, O> messageHandler;
    private final ExecutorService executor;
    private @Nullable Future<?> threadHandle;
    private @Nullable ServerLoop runnable;

    Server(final MessageHandler<I, O> handler) {
        messageHandler = handler;
        executor = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "AppLocker MessageServer");
            t.setDaemon(true);
            return t;
        });
    }

    synchronized void start() {
        LOG.log(Level.DEBUG, "Init message server");
        if (threadHandle != null) {
            throw new LockingException("The server is already running");
        }

        final ServerLoop loop = new ServerLoop();
        runnable = loop;
        threadHandle = executor.submit(loop);

        LOG.log(Level.DEBUG, "Message server initialized");
    }

    @Override
    public void close() {
        stop();
        executor.shutdown();
    }

    synchronized void stop() {
        LOG.log(Level.DEBUG, "Stopping message server");

        if (threadHandle != null) {
            // interrupting the thread closes the server socket channel
            threadHandle.cancel(true);
        }

        threadHandle = null;
        runnable = null;
        LOG.log(Level.DEBUG, "Message server stopped");
    }

    /**
     * Get server's socket port.
     *
     * @return port
     * @throws LockingException if a message server is not running or server is in exception state
     */
    synchronized int tryGetPort() {
        LOG.log(Level.DEBUG, "Requesting server port number");
        if (threadHandle != null && threadHandle.isDone()) {
            throw new LockingException("Server is in exception state for some reason");
        }
        if (runnable == null || runnable.port == -1) {
            throw new LockingException("Message server is not running");
        }
        final int port = runnable.port;
        LOG.log(Level.DEBUG, "Retrieved server port number: {0}", port);
        return port;
    }

    /**
     * Blocking version of {@link #tryGetPort()}, ignores {@link LockingException} and tries to retrieve the port
     * number. This method is useful for situations where you need to retrieve the port number right after the start.
     *
     * @param timeoutMs timeout in milliseconds
     * @return port number
     * @throws LockingException if a message server is not running or server is in exception state
     * @throws InterruptedException if the thread was interrupted while waiting
     */
    int getPort(final long timeoutMs) throws InterruptedException {
        final long start = System.nanoTime();
        final long timeoutNs = TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        while (true) {
            try {
                return tryGetPort();
            } catch (LockingException ex) {
                if (System.nanoTime() - start >= timeoutNs) {
                    throw new LockingException(format("Port retrieval timeout=%dms exceeded", timeoutMs), ex);
                }
                Thread.sleep(PORT_SLEEP_TIMEOUT_MS);
            }
        }
    }

    final class ServerLoop implements Runnable {
        private volatile int port = -1;

        @Override
        public void run() {
            LOG.log(Level.DEBUG, "Opening message server port");
            // use a socket channel, because it'll throw ClosedByInterruptException on interrupt
            try (ServerSocketChannel socket = ServerSocketChannel.open()) {
                final ServerSocket realSocket = socket.socket();
                realSocket.setReuseAddress(true);
                realSocket.bind(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0));
                socket.configureBlocking(true);
                port = realSocket.getLocalPort();

                LOG.log(Level.DEBUG, "Started message server on localhost:{0}", port);

                while (!Thread.currentThread().isInterrupted()) {
                    final SocketChannel channel;
                    try {
                        channel = socket.accept();
                    } catch (ClosedChannelException ex) {
                        // server was stopped
                        break;
                    }
                    handleConnection(channel);
                }
            } catch (IOException ex) {
                LOG.log(Level.ERROR, "Message server socket failure", ex);
                throw new LockingException("Message server socket failure", ex);
            }
            LOG.log(Level.DEBUG, "Message server loop finished");
        }

        private Socket configure(final Socket socket) throws IOException {
            socket.setSoTimeout(REQUEST_TIMEOUT_MS);
            return socket;
        }

        @SuppressWarnings("unchecked")
        private void handleConnection(final SocketChannel channel) {
            try (SocketChannel ch = channel;
                    Socket connSocket = configure(ch.socket());
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
                // a failure of a single connection must not terminate the server
                LOG.log(Level.WARNING, "Unable to process incoming message", ex);
            }
        }
    }
}

package io.github.sanyarnd.applocker;

import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

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

/**
 * Socket-based server.
 *
 * @param <I> receive message type
 * @param <O> response message type
 * @author Alexander Biryukov
 */
@Slf4j
final class Server<I extends Serializable, O extends Serializable> {
    private final @NotNull MessageHandler<I, O> messageHandler;
    private final @NotNull Runtime runtime;
    private final @NotNull Thread shutdownHook;
    private @Nullable Future<?> threadHandle;
    private @Nullable ServerLoop runnable;

    Server(final @NotNull MessageHandler<I, O> handler) {
        messageHandler = handler;
        runtime = Runtime.getRuntime();

        shutdownHook = new Thread(() -> stop(true), "AppLocker MessageServer shutdownHook");
    }

    void start() {
        log.debug("Init message server");
        if (threadHandle != null) {
            throw new LockingCommunicationException("The server is already running");
        }

        final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            final Thread t = new Thread(r, "AppLocker MessageServer");
            t.setDaemon(true);
            return t;
        });
        runnable = new ServerLoop();
        threadHandle = executor.submit(runnable);

        runtime.addShutdownHook(shutdownHook);
        log.debug("Message server initialized");
    }

    void stop() {
        stop(false);
    }

    private void stop(final boolean internal) {
        log.debug("Stopping message server");
        if (!internal) {
            runtime.removeShutdownHook(shutdownHook);
        }

        if (threadHandle != null) {
            threadHandle.cancel(true);
        }
        threadHandle = null;
        runnable = null;
        log.debug("Message server stopped");
    }

    /**
     * Get server's socket port.
     *
     * @return port
     * @throws LockingCommunicationException if message server is not running
     * @throws LockingMessageServerException if server is in exception state (also possible race with {@link #stop()}
     *                                       call)
     */
    int getPort() {
        log.debug("Requesting server port number");
        if (threadHandle != null && threadHandle.isDone()) {
            throw new LockingMessageServerException("Server is in exception state for some reason");
        }
        if (runnable == null || runnable.port == -1) {
            throw new LockingCommunicationException("Message server is not running");
        }
        log.debug("Retrieved server port number: {}", runnable.port);
        return runnable.port;
    }

    /**
     * Blocking version of {@link #getPort()}, ignores {@link LockingCommunicationException} and tries to retrieve the
     * port number.<br> This method is useful for situations where you need to retrieve the port number right after the
     * start
     *
     * @return port number
     * @throws LockingMessageServerException if server is in exception state (also possible race with {@link #stop()}
     *                                       call)
     */
    int getPortBlocking() {
        while (true) {
            try {
                return getPort();
            } catch (LockingCommunicationException ex) {
                // ignore exception, wait until socket is open
            }
        }
    }

    final class ServerLoop implements Runnable {
        private volatile int port = -1;

        @Override
        public void run() {
            log.debug("Opening message server port");
            // use socket channel, because it'll throw ClosedByInterruptException on interrupt
            try (ServerSocketChannel socket = ServerSocketChannel.open();
                 ServerSocket realSocket = socket.socket()) {
                realSocket.setReuseAddress(true);
                realSocket.bind(new InetSocketAddress(0));
                port = realSocket.getLocalPort();
                socket.configureBlocking(true);
                log.info("Staring message server on localhost:{}", port);

                while (!Thread.currentThread().isInterrupted()) {
                    try (SocketChannel channel = socket.accept();
                         Socket connSocket = channel.socket();
                         ObjectOutputStream oos = new ObjectOutputStream(connSocket.getOutputStream());
                         ObjectInputStream ois = new ObjectInputStream(connSocket.getInputStream())) {
                        log.debug("New connection from localhost:{}", connSocket.getPort());
                        try {
                            @SuppressWarnings("unchecked") final I message = (I) ois.readObject();
                            log.debug("Incoming message: {}", message);
                            try {
                                final O answer = messageHandler.handleMessage(message);
                                log.debug("Calculated answer: {}", answer);
                                oos.writeObject(answer);
                            } catch (RuntimeException ex) {
                                log.error("Error during processing message {}", message, ex);
                            }
                        } catch (IOException | ClassNotFoundException ex) {
                            // there's a failure during de-serialization or handling the message
                            // but we don't want to terminate the server
                            log.error("Error during deserialization", ex);
                        }
                    }
                }
            } catch (IOException ex) {
                // something wrong happened with socket
                log.error("Cannot initialize the socket", ex);
                throw new RuntimeException(ex);
            }
        }
    }
}

package io.github.sanyarnd.applocker;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetSocketAddress;
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
final class Server<I extends Serializable, O extends Serializable> {
    private final @NotNull MessageHandler<I, O> messageHandler;
    private final @NotNull Runtime runtime;
    private final @NotNull Thread shutdownHook;
    private @Nullable Future<?> threadHandle;
    private @Nullable ServerLoop runnable;

    Server(final @NotNull MessageHandler<I, O> handler) {
        messageHandler = handler;
        runtime = Runtime.getRuntime();

        shutdownHook = new Thread(() -> stop(true), "Message server `%s` shutdownHook");
    }

    void start() {
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
    }

    void stop() {
        stop(false);
    }

    private void stop(final boolean internal) {
        if (!internal) {
            runtime.removeShutdownHook(shutdownHook);
        }

        if (threadHandle != null) {
            threadHandle.cancel(true);
        }
        threadHandle = null;
        runnable = null;
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
        if (threadHandle != null && threadHandle.isDone()) {
            throw new LockingMessageServerException("Server is in exception state for some reason");
        }
        if (runnable == null || runnable.port == -1) {
            throw new LockingCommunicationException("Message server is not running");
        }
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

    public final class ServerLoop implements Runnable {
        private int port = -1;

        @Override
        public void run() {
            // use socket channel, because it'll throw ClosedByInterruptException on interrupt
            try (ServerSocketChannel socket = ServerSocketChannel.open()) {
                socket.socket().setReuseAddress(true);
                socket.socket().bind(new InetSocketAddress(0));
                port = socket.socket().getLocalPort();
                socket.configureBlocking(true);

                while (!Thread.currentThread().isInterrupted()) {
                    try (SocketChannel channel = socket.accept();
                         ObjectOutputStream oos = new ObjectOutputStream(channel.socket().getOutputStream());
                         ObjectInputStream ois = new ObjectInputStream(channel.socket().getInputStream())) {
                        try {
                            @SuppressWarnings("unchecked") final I message = (I) ois.readObject();
                            final O answer = messageHandler.handleMessage(message);
                            oos.writeObject(answer);
                        } catch (IOException | ClassNotFoundException ignored) {
                            // there's a failure during de-serialization or handling the message
                            // but we don't want to terminate the server
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }
}

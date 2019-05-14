// This is an open source non-commercial project. Dear PVS-Studio, please check it.
// PVS-Studio Static Code Analyzer for C, C++, C#, and Java: http://www.viva64.com
//
//                     Copyright 2019 Alexander Biryukov
//
//     Licensed under the Apache License, Version 2.0 (the "License");
//     you may not use this file except in compliance with the License.
//     You may obtain a copy of the License at
//
//               http://www.apache.org/licenses/LICENSE-2.0
//
//     Unless required by applicable law or agreed to in writing, software
//     distributed under the License is distributed on an "AS IS" BASIS,
//     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//     See the License for the specific language governing permissions and
//     limitations under the License.

package io.github.sanyarnd.applocker.messaging;

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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import io.github.sanyarnd.applocker.exceptions.LockingCommunicationException;
import io.github.sanyarnd.applocker.exceptions.LockingMessageServerException;

/**
 * Socket-based server
 *
 * @author Alexander Biryukov
 */
public final class Server<I extends Serializable, O extends Serializable> {
    @Nonnull
    private final MessageHandler<I, O> handler;
    @Nonnull
    private final Runtime runtime;
    @Nullable
    private Future<?> threadHandle;
    @Nullable
    private ServerLoop runnable;
    @Nonnull
    private Thread shutdownHook;

    public Server(@Nonnull MessageHandler<I, O> handler) {
        this.handler = handler;
        runtime = Runtime.getRuntime();

        shutdownHook = new Thread(() -> stop(true), "Message server `%s` shutdownHook");
    }


    public void start() {
        if (threadHandle != null) {
            throw new LockingCommunicationException("The server is already running");
        }

        ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "AppLocker MessageServer");
            t.setDaemon(true);
            return t;
        });
        runnable = new ServerLoop();
        threadHandle = executor.submit(runnable);

        runtime.addShutdownHook(shutdownHook);
    }

    public void stop() {
        stop(false);
    }

    private void stop(boolean internal) {
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
     * Get server's socket port
     *
     * @return port
     * @throws LockingCommunicationException if message server is not running
     * @throws LockingMessageServerException if server is in exception state (also possible race with {@link #stop()} call)
     */
    public int getPort() {
        if (threadHandle != null && threadHandle.isDone()) {
            throw new LockingMessageServerException("Server is in exception state for some reason");
        }
        if (runnable == null || runnable.port == -1) {
            throw new LockingCommunicationException("Message server is not running");
        }
        return runnable.port;
    }

    /**
     * Blocking version of {@link #getPort()}, ignores {@link LockingCommunicationException}
     * and tries to retrieve the port number.<br/>
     * This method is useful for situations where you need to retrieve the port number right after the start
     *
     * @return port number
     * @throws LockingMessageServerException if server is in exception state (also possible race with {@link #stop()} call)
     */
    public int getPortBlocking() {
        while (true) {
            try {
                return getPort();
            } catch (LockingCommunicationException ex) {
                // we need to block till socket is opened somehow
            }
        }
    }

    public final class ServerLoop implements Runnable {
        private int port = -1;

        @Override
        public void run() {
            // use socket channel, because it'll throw ClosedByInterruptException
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
                            @SuppressWarnings("unchecked")
                            I message = (I) ois.readObject();
                            O answer = handler.handleMessage(message);
                            oos.writeObject(answer);
                        } catch (IOException | ClassNotFoundException ignored) {
                            // there's a failure during de-serialization,
                            // but we don't want to terminate the server
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
    }
}

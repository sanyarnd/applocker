package io.github.sanyarnd.applocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import org.instancio.Instancio;
import org.instancio.junit.Given;
import org.instancio.junit.InstancioExtension;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@Timeout(value = 60, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
@ExtendWith({MockitoExtension.class, InstancioExtension.class})
class ServerClientTest {
    private static final long PORT_TIMEOUT_MS = 5_000;

    private final List<Server<?, ?>> servers = new ArrayList<>();

    @Mock
    private MessageHandler<String, String> handler;

    record Payload(String name, int count, List<String> tags) implements Serializable {}

    @AfterEach
    void stopServers() {
        servers.forEach(Server::close);
    }

    private <I extends Serializable, O extends Serializable> int startServer(final MessageHandler<I, O> h)
            throws InterruptedException {
        final Server<I, O> server = new Server<>(h);
        servers.add(server);
        server.start();
        return server.getPort(PORT_TIMEOUT_MS);
    }

    @RepeatedTest(10)
    void echoesStrings(@Given final String message) throws InterruptedException {
        final int port = startServer((MessageHandler<String, String>) m -> m);

        final String answer = new Client<String, String>(port).send(message);

        assertThat(answer).isEqualTo(message);
    }

    @Test
    void echoesComplexObjects() throws InterruptedException {
        final Payload payload = Instancio.create(Payload.class);
        final int port = startServer((MessageHandler<Payload, Payload>) m -> m);

        final Payload answer = new Client<Payload, Payload>(port).send(payload);

        assertThat(answer).isEqualTo(payload).isNotSameAs(payload);
    }

    @Test
    void passesMessageToHandlerAndReturnsItsAnswer() throws InterruptedException {
        when(handler.handleMessage("ping")).thenReturn("pong");
        final int port = startServer(handler);

        assertThat(new Client<String, String>(port).send("ping")).isEqualTo("pong");
        verify(handler).handleMessage("ping");
    }

    @Test
    void servesManyClientsSequentially() throws InterruptedException {
        final int port = startServer((MessageHandler<Integer, Integer>) m -> m * 2);

        for (int i = 0; i < 20; ++i) {
            assertThat(new Client<Integer, Integer>(port).send(i)).isEqualTo(i * 2);
        }
    }

    @Test
    void handlerExceptionIsReportedToClientAndServerSurvives() throws InterruptedException {
        when(handler.handleMessage(any()))
                .thenThrow(new IllegalArgumentException("boom"))
                .thenReturn("ok");
        final int port = startServer(handler);
        final Client<String, String> client = new Client<>(port);

        assertThatThrownBy(() -> client.send("first")).isInstanceOf(LockingException.class);
        assertThat(client.send("second")).isEqualTo("ok");
    }

    @Test
    void wrongMessageTypeIsReportedToClientAndServerSurvives() throws InterruptedException {
        final MessageHandler<String, String> stringHandler = m -> m.toUpperCase(Locale.ROOT);
        final int port = startServer(stringHandler);

        assertThatThrownBy(() -> new Client<Integer, String>(port).send(42)).isInstanceOf(LockingException.class);
        assertThat(new Client<String, String>(port).send("ok")).isEqualTo("OK");
    }

    @Test
    void garbageInputDoesNotBreakServer() throws Exception {
        final int port = startServer((MessageHandler<String, String>) m -> m);

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            final OutputStream out = socket.getOutputStream();
            out.write("definitely not a java serialization stream".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        assertThat(new Client<String, String>(port).send("alive")).isEqualTo("alive");
    }

    @Test
    void silentClientDoesNotBlockServerForever() throws Exception {
        final int port = startServer((MessageHandler<String, String>) m -> m);

        try (Socket silent = new Socket(InetAddress.getLoopbackAddress(), port)) {
            // the silent client holds the only server thread until the request timeout expires
            assertThat(silent.isConnected()).isTrue();
            assertThat(new Client<String, String>(port).send("alive")).isEqualTo("alive");
        }
    }

    @Test
    void clientTimesOutIfServerDoesNotAnswer() throws Exception {
        try (ServerSocket mute = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            final Client<String, String> client = new Client<>(mute.getLocalPort(), 200);

            assertThatThrownBy(() -> client.send("hello"))
                    .isExactlyInstanceOf(LockingException.class)
                    .hasMessageContaining("did not answer in time");
        }
    }

    @Test
    void abruptDisconnectDoesNotBreakServer() throws Exception {
        final int port = startServer((MessageHandler<String, String>) m -> m);

        // connect and immediately disconnect
        new Socket(InetAddress.getLoopbackAddress(), port).close();

        assertThat(new Client<String, String>(port).send("alive")).isEqualTo("alive");
    }

    @Test
    void serverIsNotReachableFromNonLoopbackAddress() throws Exception {
        final Optional<InetAddress> external = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .flatMap(nic -> Collections.list(nic.getInetAddresses()).stream())
                .filter(address -> !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                .findFirst();
        assumeTrue(external.isPresent(), "no non-loopback network interface available");
        final int port = startServer((MessageHandler<String, String>) m -> m);

        try (Socket socket = new Socket()) {
            assertThatThrownBy(() -> socket.connect(new InetSocketAddress(external.get(), port), 2_000))
                    .isInstanceOf(IOException.class);
        }
        assertThat(new Client<String, String>(port).send("loopback")).isEqualTo("loopback");
    }

    @Test
    void clientFailsWithoutServer() throws Exception {
        final int freePort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            freePort = socket.getLocalPort();
        }
        final Client<String, String> client = new Client<>(freePort);

        assertThatThrownBy(() -> client.send("test"))
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("Unable to connect");
    }

    @Test
    void portIsUnavailableBeforeStart() {
        final Server<String, String> server = new Server<>(handler);
        servers.add(server);

        assertThatThrownBy(server::tryGetPort)
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("not running");
    }

    @Test
    void getPortTimesOutIfServerIsNotStarted() {
        final Server<String, String> server = new Server<>(handler);
        servers.add(server);

        assertThatThrownBy(() -> server.getPort(50))
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("timeout=50ms");
    }

    @Test
    void startTwiceFails() throws InterruptedException {
        final Server<String, String> server = new Server<>(handler);
        servers.add(server);
        server.start();

        assertThatThrownBy(server::start).isExactlyInstanceOf(LockingException.class);
    }

    @Test
    void stopClosesServerSocket() throws Exception {
        final Server<String, String> server = new Server<>(handler);
        servers.add(server);
        server.start();
        final int port = server.getPort(PORT_TIMEOUT_MS);

        server.stop();

        assertThatThrownBy(server::tryGetPort).isExactlyInstanceOf(LockingException.class);
        // the port is released eventually
        final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            try {
                new Client<String, String>(port).send("ping");
            } catch (LockingException ex) {
                break;
            }
            assertThat(System.nanoTime()).isLessThan(deadline);
            Thread.sleep(10);
        }
    }

    @Test
    void canBeRestarted() throws InterruptedException {
        when(handler.handleMessage(any())).thenReturn("ok");
        final Server<String, String> server = new Server<>(handler);
        servers.add(server);

        for (int i = 0; i < 3; ++i) {
            server.start();
            final int port = server.getPort(PORT_TIMEOUT_MS);
            assertThat(port).isPositive();
            assertThat(new Client<String, String>(port).send("ping")).isEqualTo("ok");
            server.stop();
            assertThatThrownBy(server::tryGetPort).isExactlyInstanceOf(LockingException.class);
        }
        verify(handler, timeout(1000).times(3)).handleMessage("ping");
    }

    @Test
    void stopWithoutStartDoesNothing() {
        final Server<String, String> server = new Server<>(handler);
        servers.add(server);

        assertThatCode(server::stop).doesNotThrowAnyException();
    }
}

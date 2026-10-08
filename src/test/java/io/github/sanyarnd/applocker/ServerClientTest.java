package io.github.sanyarnd.applocker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
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
    private static final byte[] TOKEN = Protocol.newToken();
    private static final int TIMEOUT_MS = 10_000;

    private final List<Server> servers = new ArrayList<>();

    @Mock
    private MessageHandler handler;

    @AfterEach
    void stopServers() {
        servers.forEach(Server::close);
    }

    private Server server(final MessageHandler h) {
        final Server server = new Server(h);
        servers.add(server);
        return server;
    }

    private int startServer(final MessageHandler h) {
        return server(h).start(TOKEN);
    }

    private static Client client(final int port) {
        return new Client(port, TOKEN, TIMEOUT_MS);
    }

    @RepeatedTest(10)
    void echoesStrings(@Given final String message) {
        final int port = startServer(m -> m);

        assertThat(client(port).send(message)).isEqualTo(message);
    }

    @Test
    void echoesUnicodeAndEmptyStrings() {
        final int port = startServer(m -> m);

        assertThat(client(port).send("привет, 世界 👋")).isEqualTo("привет, 世界 👋");
        assertThat(client(port).send("")).isEmpty();
    }

    @Test
    void passesMessageToHandlerAndReturnsItsAnswer() {
        when(handler.handleMessage("ping")).thenReturn("pong");
        final int port = startServer(handler);

        assertThat(client(port).send("ping")).isEqualTo("pong");
        verify(handler).handleMessage("ping");
    }

    @Test
    void servesManyClientsSequentially() {
        final int port = startServer(m -> m + "!");

        for (int i = 0; i < 20; ++i) {
            assertThat(client(port).send(String.valueOf(i))).isEqualTo(i + "!");
        }
    }

    @Test
    void handlerExceptionIsReportedToClientAndServerSurvives() {
        when(handler.handleMessage(any()))
                .thenThrow(new IllegalArgumentException("boom"))
                .thenReturn("ok");
        final int port = startServer(handler);

        assertThatThrownBy(() -> client(port).send("first")).isInstanceOf(LockingException.class);
        assertThat(client(port).send("second")).isEqualTo("ok");
    }

    @Test
    void rejectsClientWithWrongToken() {
        final int port = startServer(handler);

        assertThatThrownBy(() -> new Client(port, Protocol.newToken(), TIMEOUT_MS).send("hello"))
                .isExactlyInstanceOf(LockingException.class);
        verifyNoInteractions(handler);
    }

    @Test
    void clientRejectsTooLargeMessage() {
        final int port = startServer(handler);
        final String message = "x".repeat(Protocol.MAX_MESSAGE_BYTES + 1);

        assertThatThrownBy(() -> client(port).send(message))
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("exceeds");
        verifyNoInteractions(handler);
    }

    @Test
    void acceptsMessageOfMaximalSize() {
        final int port = startServer(m -> m);
        final String message = "x".repeat(Protocol.MAX_MESSAGE_BYTES);

        assertThat(client(port).send(message)).hasSize(Protocol.MAX_MESSAGE_BYTES);
    }

    @Test
    void serverRejectsTooLargeLengthAndSurvives() throws Exception {
        final int port = startServer(m -> m);

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port);
                DataOutputStream out = new DataOutputStream(socket.getOutputStream())) {
            out.write(TOKEN);
            out.writeInt(Integer.MAX_VALUE);
            out.flush();
            assertThat(socket.getInputStream().read()).isEqualTo(-1);
        }

        assertThat(client(port).send("alive")).isEqualTo("alive");
    }

    @Test
    void tooLargeAnswerIsReportedToClient() {
        final int port = startServer(m -> "x".repeat(Protocol.MAX_MESSAGE_BYTES + 1));

        assertThatThrownBy(() -> client(port).send("hello")).isExactlyInstanceOf(LockingException.class);
    }

    @Test
    void garbageInputDoesNotBreakServer() throws Exception {
        final int port = startServer(m -> m);

        try (Socket socket = new Socket(InetAddress.getLoopbackAddress(), port)) {
            final OutputStream out = socket.getOutputStream();
            out.write("definitely not a valid request".getBytes(StandardCharsets.UTF_8));
            out.flush();
        }

        assertThat(client(port).send("alive")).isEqualTo("alive");
    }

    @Test
    void silentClientDoesNotBlockServerForever() throws Exception {
        final int port = startServer(m -> m);

        try (Socket silent = new Socket(InetAddress.getLoopbackAddress(), port)) {
            assertThat(silent.isConnected()).isTrue();
            assertThat(client(port).send("alive")).isEqualTo("alive");
        }
    }

    @Test
    void clientTimesOutIfServerDoesNotAnswer() throws Exception {
        try (ServerSocket mute = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            final Client client = new Client(mute.getLocalPort(), TOKEN, 200);

            assertThatThrownBy(() -> client.send("hello"))
                    .isExactlyInstanceOf(LockingException.class)
                    .hasMessageContaining("did not answer in time");
        }
    }

    @Test
    void abruptDisconnectDoesNotBreakServer() throws Exception {
        final int port = startServer(m -> m);

        new Socket(InetAddress.getLoopbackAddress(), port).close();

        assertThat(client(port).send("alive")).isEqualTo("alive");
    }

    @Test
    void serverIsNotReachableFromNonLoopbackAddress() throws Exception {
        final Optional<InetAddress> external = Collections.list(NetworkInterface.getNetworkInterfaces()).stream()
                .flatMap(nic -> Collections.list(nic.getInetAddresses()).stream())
                .filter(address -> !address.isLoopbackAddress() && !address.isLinkLocalAddress())
                .findFirst();
        assumeTrue(external.isPresent(), "no non-loopback network interface available");
        final int port = startServer(m -> m);

        try (Socket socket = new Socket()) {
            assertThatThrownBy(() -> socket.connect(new InetSocketAddress(external.get(), port), 2_000))
                    .isInstanceOf(IOException.class);
        }
        assertThat(client(port).send("loopback")).isEqualTo("loopback");
    }

    @Test
    void clientFailsWithoutServer() throws Exception {
        final int freePort;
        try (ServerSocket socket = new ServerSocket(0, 1, InetAddress.getLoopbackAddress())) {
            freePort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> client(freePort).send("test"))
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("Unable to connect");
    }

    @Test
    void portIsUnavailableBeforeStart() {
        final Server server = server(handler);

        assertThatThrownBy(server::getPort)
                .isExactlyInstanceOf(LockingException.class)
                .hasMessageContaining("not running");
    }

    @Test
    void startReturnsPort() {
        final Server server = server(handler);

        final int port = server.start(TOKEN);

        assertThat(port).isPositive().isEqualTo(server.getPort());
    }

    @Test
    void startTwiceFails() {
        final Server server = server(handler);
        server.start(TOKEN);

        assertThatThrownBy(() -> server.start(TOKEN)).isExactlyInstanceOf(LockingException.class);
    }

    @Test
    void stopClosesServerSocket() {
        final Server server = server(handler);
        final int port = server.start(TOKEN);

        server.stop();

        assertThatThrownBy(server::getPort).isExactlyInstanceOf(LockingException.class);
        assertThatThrownBy(() -> client(port).send("ping")).isExactlyInstanceOf(LockingException.class);
    }

    @Test
    void canBeRestartedWithNewToken() {
        when(handler.handleMessage(any())).thenReturn("ok");
        final Server server = server(handler);

        for (int i = 0; i < 3; ++i) {
            final byte[] token = Protocol.newToken();
            final int port = server.start(token);
            assertThat(new Client(port, token, TIMEOUT_MS).send("ping")).isEqualTo("ok");
            server.stop();
            assertThatThrownBy(server::getPort).isExactlyInstanceOf(LockingException.class);
        }
        verify(handler, timeout(1000).times(3)).handleMessage("ping");
    }

    @Test
    void stopWithoutStartDoesNothing() {
        final Server server = server(handler);

        assertThatCode(server::stop).doesNotThrowAnyException();
    }
}

package io.github.sanyarnd.applocker;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.ArrayList;

class MessagingTest {
    private static <T extends Serializable> MessageHandler<T, T> createEchoHandler() {
        return message -> message;
    }

    @Test
    void send_and_receive_string() {
        MessageHandler<String, String> echoHandler = createEchoHandler();
        Server<String, String> server = new Server<>(echoHandler);
        server.start();

        int port = server.getPortBlocking();

        Client<String, String> client = new Client<>(port);

        String message = "test";
        String answer = client.send(message);
        Assertions.assertEquals(message, answer);
    }

    @Test
    void send_and_receive_array_list() {
        MessageHandler<ArrayList<Integer>, ArrayList<Integer>> echoHandler = createEchoHandler();
        Server<ArrayList<Integer>, ArrayList<Integer>> server = new Server<>(echoHandler);
        server.start();

        int port = server.getPortBlocking();

        Client<ArrayList<Integer>, ArrayList<Integer>> client = new Client<>(port);

        ArrayList<Integer> message = new ArrayList<>();
        message.add(1);
        ArrayList<Integer> answer = client.send(message);
        Assertions.assertEquals(message, answer);
    }

    @Test
    void send_and_receive_multiple_clients() {
        MessageHandler<String, String> echoHandler = createEchoHandler();
        Server<String, String> server = new Server<>(echoHandler);
        server.start();

        int port = server.getPortBlocking();

        Client<String, String> client = new Client<>(port);
        Client<String, String> client2 = new Client<>(port);

        String message1 = "test";
        String answer1 = client.send(message1);
        Assertions.assertEquals(message1, answer1);
        answer1 = client.send(message1);
        Assertions.assertEquals(message1, answer1);

        String message2 = "test2";
        String answer2 = client2.send(message2);
        Assertions.assertEquals(message2, answer2);
        answer2 = client2.send(message2);
        Assertions.assertEquals(message2, answer2);
    }

    @Test
    void client_throws_if_no_server() {
        final Client<String, String> client = new Client<>(0);

        final String message = "test";
        Assertions.assertThrows(LockingCommunicationException.class, () -> client.send(message));
    }

    @Test
    void port_throws_if_no_server() {
        final Server<String, String> server = new Server<>(createEchoHandler());

        Assertions.assertThrows(LockingCommunicationException.class, server::getPort);
    }

    @Test
    void port_throws_if_server_exception() {
        final Server<String, String> server = new Server<>(e -> {
            throw new IllegalArgumentException();
        });

        server.start();
        final Client<String, String> client = new Client<>(server.getPortBlocking());
        Assertions.assertThrows(LockingCommunicationException.class, () -> client.send("test"));
        Assertions.assertThrows(LockingMessageServerException.class, server::getPortBlocking);
        Assertions.assertThrows(LockingCommunicationException.class, () -> client.send("test"));
    }

    @Test
    void client_throws_1_if_types_do_not_match() {
        MessageHandler<String, String> echoHandler = createEchoHandler();
        Server<String, String> server = new Server<>(echoHandler);
        server.start();

        int port = server.getPortBlocking();

        final Client<Integer, String> client = new Client<>(port);

        final Integer message = 1;

        // cast exception happens here apparently
        // because of the type erasure inside generic method
        Assertions.assertThrows(ClassCastException.class, () -> {
            String answer = client.send(message);
        });
    }

    @Test
    void client_throws_2_if_types_do_not_match() {
        // we need to directly create string overloaded version instead of using createEchoHandler
        // otherwise there won't be exception inside this handler when types won't match
        MessageHandler<String, String> handler = message -> message;
        Server<String, String> server = new Server<>(handler);
        server.start();

        int port = server.getPortBlocking();

        final Client<Integer, String> client = new Client<>(port);

        final Integer message = 1231231231;
        Assertions.assertThrows(LockingCommunicationException.class, () -> {
            String answer = client.send(message);
        });
    }
}

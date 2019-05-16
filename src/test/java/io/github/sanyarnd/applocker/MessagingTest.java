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

package io.github.sanyarnd.applocker;

import java.io.Serializable;
import java.util.ArrayList;

import javax.annotation.Nonnull;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import io.github.sanyarnd.applocker.exceptions.LockingCommunicationException;
import io.github.sanyarnd.applocker.exceptions.LockingMessageServerException;


public class MessagingTest {
    private static <T extends Serializable> MessageHandler<T, T> createEchoHandler() {
        return new MessageHandler<T, T>() {
            @Nonnull
            @Override
            public T handleMessage(@Nonnull T message) {
                return message;
            }
        };
    }

    @Test
    public void send_and_receive_string() {
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
    public void send_and_receive_array_list() {
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
    public void send_and_receive_multiple_clients() {
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
    public void client_throws_if_no_server() {
        final Client<String, String> client = new Client<>(0);

        final String message = "test";
        Assertions.assertThrows(LockingCommunicationException.class, () -> client.send(message));
    }

    @Test
    public void port_throws_if_no_server() {
        final Server<String, String> server = new Server<>(createEchoHandler());

        Assertions.assertThrows(LockingCommunicationException.class, server::getPort);
    }

    @Test
    public void port_throws_if_server_exception() {
        final Server<String, String> server = new Server<>(e -> {
            throw new IllegalArgumentException();
        });

        server.start();
        final Client<String, String> client = new Client<>(server.getPortBlocking());
        Assertions.assertThrows(LockingCommunicationException.class, () -> client.send("test"));
        Assertions.assertThrows(LockingMessageServerException.class, server::getPortBlocking);
    }

    @Test
    public void client_throws_1_if_types_do_not_match() {
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
    public void client_throws_2_if_types_do_not_match() {
        // we need to directly create string overloaded version instead of using createEchoHandler
        // otherwise there won't be exception inside this handler when types won't match
        MessageHandler<String, String> handler = new MessageHandler<String, String>() {
            @Nonnull
            @Override
            public String handleMessage(@Nonnull String message) {
                return message;
            }
        };
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

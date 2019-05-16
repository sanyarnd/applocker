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

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.net.InetAddress;
import java.net.Socket;

import javax.annotation.Nonnull;

import io.github.sanyarnd.applocker.exceptions.LockingCommunicationException;

/**
 * Client who can communicate with {@link Server} object
 *
 * @author Alexander Biryukov
 */
final class Client<I extends Serializable, O extends Serializable> {
    private final int port;

    public Client(int port) {
        this.port = port;
    }

    @Nonnull
    public O send(@Nonnull I message) {
        try (Socket socket = new Socket(InetAddress.getLocalHost(), port);
             ObjectInputStream input = new ObjectInputStream(socket.getInputStream());
             ObjectOutputStream output = new ObjectOutputStream(socket.getOutputStream())) {

            output.writeObject(message);

            @SuppressWarnings("unchecked")
            O ret = (O) input.readObject();

            return ret;
        } catch (IOException | ClassNotFoundException ex) {
            throw new LockingCommunicationException(ex);
        }
    }
}

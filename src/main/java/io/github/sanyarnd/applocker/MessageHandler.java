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

import org.checkerframework.checker.nullness.qual.NonNull;

/**
 * Interface for the function that runs on the server side
 * and handles all incoming messages.
 *
 * @param <I> message type
 * @param <O> answer type
 */
@FunctionalInterface
public interface MessageHandler<I extends Serializable, O extends Serializable> {
    /**
     * Handle the received message and return the result.
     *
     * @param message input message
     * @return result of the message processing
     */
    @NonNull
    O handleMessage(@NonNull I message);
}

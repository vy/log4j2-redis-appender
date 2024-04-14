/*
 * Copyright 2017-2024 Volkan Yazıcı
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permits and
 * limitations under the License.
 */
package com.vlkan.log4j2.redis.appender;

import java.net.InetAddress;
import java.net.ServerSocket;

final class NetworkUtils {

    // We used to have code that search for the local host name, but the caused issues on Windows where it would resolve the computer name instead of `localhost`.
    // By resolving the computer name, the emulator would try to use the host address instead of `127.0.0.1`, which prevents network connections, specifically when using the VPN.
    // The hardcoded `localhost` will probably always work, so we want to try that for now.
    private static final String LOCAL_HOST_NAME = "localhost";

    private NetworkUtils() {}

    static String localHostName() {
        return LOCAL_HOST_NAME;
    }

    static int findUnusedPort(String hostName) {
        try {
            InetAddress bindAddress = InetAddress.getByName(hostName);
            try (ServerSocket socket = new ServerSocket(0, 0, bindAddress)) {
                socket.setReuseAddress(true);
                return socket.getLocalPort();
            }
        } catch (Exception error) {
            throw new RuntimeException("failed to find an unused port", error);
        }
    }

}

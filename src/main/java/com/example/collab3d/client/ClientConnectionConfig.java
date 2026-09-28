package com.example.collab3d.client;

import com.example.collab3d.common.NetworkConstants;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable runtime connection settings for a collaboration client.
 *
 * <p>The prototype originally used a hard-coded localhost endpoint. Keeping
 * connection details in this small neutral value object lets the same client
 * executable connect to localhost, another workstation on the LAN, or a
 * remotely hosted server without changing or rebuilding the application.</p>
 *
 * <p>JavaFX named command-line parameters are supported:</p>
 *
 * <pre>
 * --host=192.168.1.50
 * --tcpPort=6143
 * --udpPort=6144
 * </pre>
 *
 * <p>Any omitted setting falls back to {@link NetworkConstants}. This keeps
 * the existing zero-configuration localhost development workflow intact.</p>
 *
 * @param host server DNS name or IP address
 * @param tcpPort SpiderMonkey reliable/TCP port
 * @param udpPort SpiderMonkey unreliable/UDP port
 */
record ClientConnectionConfig(
        String host,
        int tcpPort,
        int udpPort) {

    ClientConnectionConfig {
        Objects.requireNonNull(host, "host");
        host = host.strip();
        if (host.isEmpty()) {
            throw new IllegalArgumentException("host must not be blank");
        }
        validatePort("tcpPort", tcpPort);
        validatePort("udpPort", udpPort);
    }

    /**
     * Creates configuration from JavaFX named command-line parameters.
     *
     * @param named named parameters supplied by Application.Parameters
     * @return validated connection configuration
     */
    static ClientConnectionConfig from(Map<String, String> named) {
        Objects.requireNonNull(named, "named");

        String host = valueOrDefault(
                named.get("host"),
                NetworkConstants.DEFAULT_HOST);

        int tcpPort = parsePort(
                "tcpPort",
                named.get("tcpPort"),
                NetworkConstants.TCP_PORT);

        int udpPort = parsePort(
                "udpPort",
                named.get("udpPort"),
                NetworkConstants.UDP_PORT);

        return new ClientConnectionConfig(host, tcpPort, udpPort);
    }

    /**
     * Human-readable endpoint used by status and diagnostic messages.
     */
    String endpointDescription() {
        if (tcpPort == udpPort) {
            return host + ":" + tcpPort;
        }
        return host + " (TCP " + tcpPort + ", UDP " + udpPort + ")";
    }

    private static String valueOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank()
                ? defaultValue
                : value.strip();
    }

    private static int parsePort(
            String parameterName,
            String value,
            int defaultPort) {

        if (value == null || value.isBlank()) {
            return defaultPort;
        }

        try {
            int port = Integer.parseInt(value.strip());
            validatePort(parameterName, port);
            return port;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(
                    "Invalid --" + parameterName + " value: " + value,
                    exception);
        }
    }

    private static void validatePort(String parameterName, int port) {
        if (port < 1 || port > 65_535) {
            throw new IllegalArgumentException(
                    parameterName + " must be in the range 1..65535: " + port);
        }
    }
}

package com.example.collab3d.client;

/**
 * Native-packaging launcher for the JavaFX collaboration client.
 *
 * <p>This class deliberately does not extend JavaFX Application. Native
 * jpackage launchers start this ordinary Java main class, which then delegates
 * to ClientMain. This keeps JavaFX dependencies on the packaged application
 * classpath and avoids requiring the entire application to be converted to a
 * JPMS modular application solely for native packaging.</p>
 *
 * <p>All command-line arguments are passed through unchanged. For example:</p>
 *
 * <pre>
 * Collab3DClient.exe --host=192.168.1.50 --name=Client-A
 * </pre>
 */
public final class ClientLauncher {

    private ClientLauncher() {
    }

    public static void main(String[] args) {
        ClientMain.main(args);
    }
}
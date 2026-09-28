package com.example.collab3d.common;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Loads an optional {@code key=value} properties file from the current
 * working directory into a plain string map, using the same key names as
 * the client's JavaFX named command-line parameters and the server's
 * {@code --key=value} arguments.
 *
 * <p>A missing file is not an error. The properties file is purely an
 * optional convenience so a packaged executable does not need command-line
 * arguments every launch; every deployment still works from
 * {@link NetworkConstants} defaults alone.</p>
 */
public final class PropertiesFileLoader {

    private PropertiesFileLoader() {
    }

    /**
     * @param fileName path (typically just a bare file name, resolved
     *         against the current working directory) of the properties file
     * @return the file's entries as strings, or an empty map if the file
     *         does not exist or could not be read
     */
    public static Map<String, String> load(String fileName) {
        Path path = Path.of(fileName);
        Map<String, String> values = new LinkedHashMap<>();

        if (!Files.isRegularFile(path)) {
            return values;
        }

        Properties properties = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            properties.load(in);
        } catch (IOException exception) {
            System.err.println(
                    "Could not read " + fileName + ": " + exception);
            return values;
        }

        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name));
        }

        return values;
    }
}
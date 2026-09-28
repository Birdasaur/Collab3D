package com.example.collab3d.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Paths;

/**
 * @author Sean Phillips
 */
public enum ResourceUtils {
    INSTANCE;
    private static final Logger LOG = LoggerFactory.getLogger(ResourceUtils.class);

    public static String removeExtension(String filename) {
        return filename.substring(0, filename.lastIndexOf("."));
    }

    public static String getNameFromURI(String uriString) {
        try {
            return Paths.get(new URI(uriString)).getFileName().toString();
        } catch (URISyntaxException ex) {
            LOG.error("Could not load URI from: " + uriString);
        }
        return "";
    }

}

package com.spiralcaptain.prefs;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.prefs.AbstractPreferences;
import java.util.prefs.BackingStoreException;

final class FilePreferences extends AbstractPreferences {

    private static final String STORE = "node.properties";

    private final Path directory;
    private final Properties values = new Properties();
    private boolean loaded;

    FilePreferences(FilePreferences parent, String name, Path directory) {
        super(parent, name);
        this.directory = directory;
        newNode = !Files.exists(directory.resolve(STORE));
    }

    @Override
    protected void putSpi(String key, String value) {
        load();
        values.setProperty(key, value);
    }

    @Override
    protected String getSpi(String key) {
        load();
        return values.getProperty(key);
    }

    @Override
    protected void removeSpi(String key) {
        load();
        values.remove(key);
    }

    @Override
    protected String[] keysSpi() {
        load();
        return values.stringPropertyNames().toArray(new String[0]);
    }

    @Override
    protected String[] childrenNamesSpi() throws BackingStoreException {
        if (!Files.isDirectory(directory)) {
            return new String[0];
        }
        List<String> children = new ArrayList<>();
        try (var entries = Files.list(directory)) {
            entries.filter(Files::isDirectory)
                    .forEach(child -> children.add(decode(child.getFileName().toString())));
        } catch (IOException ioe) {
            throw new BackingStoreException(ioe);
        }
        return children.toArray(new String[0]);
    }

    @Override
    protected AbstractPreferences childSpi(String name) {
        return new FilePreferences(this, name, directory.resolve(encode(name)));
    }

    @Override
    protected void syncSpi() throws BackingStoreException {
        flushSpi();
    }

    @Override
    protected void flushSpi() throws BackingStoreException {
        if (!loaded && !isRemoved()) {
            return;
        }
        try {
            if (isRemoved()) {
                Files.deleteIfExists(directory.resolve(STORE));
                return;
            }
            Files.createDirectories(directory);
            try (OutputStream out = Files.newOutputStream(directory.resolve(STORE))) {
                values.store(out, "Spiral Captain isolated game preferences");
            }
        } catch (IOException ioe) {
            throw new BackingStoreException(ioe);
        }
    }

    @Override
    protected void removeNodeSpi() throws BackingStoreException {
        try {
            Files.deleteIfExists(directory.resolve(STORE));
            Files.deleteIfExists(directory);
        } catch (IOException ioe) {
            throw new BackingStoreException(ioe);
        }
    }

    private void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path store = directory.resolve(STORE);
        if (!Files.isReadable(store)) {
            return;
        }
        try (InputStream in = Files.newInputStream(store)) {
            values.load(in);
        } catch (IOException ioe) {
            System.err.println("[spiral-captain] cannot read preferences at " + store + ": " + ioe);
        }
    }

    private static String encode(String name) {
        return URLEncoder.encode(name, StandardCharsets.UTF_8);
    }

    private static String decode(String name) {
        return URLDecoder.decode(name, StandardCharsets.UTF_8);
    }
}

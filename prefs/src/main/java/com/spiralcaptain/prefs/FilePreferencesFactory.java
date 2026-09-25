package com.spiralcaptain.prefs;

import com.spiralcaptain.common.LaunchKeys;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.InvalidPreferencesFormatException;
import java.util.prefs.Preferences;
import java.util.prefs.PreferencesFactory;

public final class FilePreferencesFactory implements PreferencesFactory {

    public static final String DIRECTORY_PROPERTY = "spiralcaptain.prefsdir";

    public static final String SEED_FILE = "seed.xml";

    private static final String SEED_MARKER = "seed.imported";

    public static final String OVERRIDES_FILE = "overrides.properties";

    private static final String GAME_NODE = "projectx";

    private static final Map<String, String> FOLLOWER_VOLUME = orderedMap(
            "music_gain", "0.0",
            "effect_gain", "0.0",
            "interface_gain", "0.0");

    private static final Map<String, String> FOLLOWER_GRAPHICS = orderedMap(
            "fps_cap", "30",
            "render_quality", "LOW",
            "render_effects", "false",
            "antialiasing_level", "0");

    private static final String SAVED_VOLUME_FILE = "volume.saved";

    private static final String SAVED_GRAPHICS_FILE = "graphics.saved";

    private static final String UNSET = "unset";

    private Preferences userRoot;
    private Preferences systemRoot;

    @Override
    public synchronized Preferences userRoot() {
        if (userRoot == null) {
            Path root = root().resolve("user");
            userRoot = new FilePreferences(null, "", root);
            importSeed(root.getParent());
            applyOverrides(root.getParent());
            boolean follower = !Boolean.getBoolean(LaunchKeys.MAIN);
            applyFollowerValues(root.getParent(),
                    follower && Boolean.getBoolean(LaunchKeys.MUTE_FOLLOWERS),
                    SAVED_VOLUME_FILE, FOLLOWER_VOLUME, "sound levels");
            applyFollowerValues(root.getParent(),
                    follower && Boolean.getBoolean(LaunchKeys.LIGHT_FOLLOWERS),
                    SAVED_GRAPHICS_FILE, FOLLOWER_GRAPHICS, "graphics settings");
        }
        return userRoot;
    }

    @Override
    public synchronized Preferences systemRoot() {
        if (systemRoot == null) {
            systemRoot = new FilePreferences(null, "", root().resolve("system"));
        }
        return systemRoot;
    }

    private static Path root() {
        String configured = System.getProperty(DIRECTORY_PROPERTY);
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        System.err.println("[spiral-captain] no " + DIRECTORY_PROPERTY
                + " given; preferences will not persist");
        try {
            return Files.createTempDirectory("spiral-captain-prefs");
        } catch (IOException ioe) {
            throw new IllegalStateException("Cannot create a preferences directory", ioe);
        }
    }

    private void importSeed(Path directory) {
        Path seed = directory.resolve(SEED_FILE);
        Path marker = directory.resolve(SEED_MARKER);
        if (!Files.isReadable(seed) || Files.exists(marker)) {
            return;
        }
        try (InputStream in = Files.newInputStream(seed)) {
            Preferences.importPreferences(in);
            Files.writeString(marker, "imported\n");
        } catch (IOException | InvalidPreferencesFormatException failure) {
            System.err.println("[spiral-captain] could not seed preferences: " + failure);
        }
    }

    private void applyOverrides(Path directory) {
        Path file = directory.resolve(OVERRIDES_FILE);
        if (!Files.isReadable(file)) {
            return;
        }
        Properties overrides = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            overrides.load(in);
        } catch (IOException ioe) {
            System.err.println("[spiral-captain] could not read overrides: " + ioe);
            return;
        }
        Preferences game = userRoot.node(GAME_NODE);
        for (String key : overrides.stringPropertyNames()) {
            game.put(key, overrides.getProperty(key));
        }
        try {
            game.flush();
        } catch (BackingStoreException bse) {
            System.err.println("[spiral-captain] could not save overrides: " + bse);
        }
    }

    private void applyFollowerValues(Path directory, boolean enabled, String savedName,
            Map<String, String> followerValues, String what) {
        Preferences game = userRoot.node(GAME_NODE);
        Path saved = directory.resolve(savedName);
        try {
            if (enabled) {
                if (!Files.exists(saved)) {
                    Properties originals = new Properties();
                    for (String key : followerValues.keySet()) {
                        originals.setProperty(key, game.get(key, UNSET));
                    }
                    try (OutputStream out = Files.newOutputStream(saved)) {
                        originals.store(out, null);
                    }
                }
                followerValues.forEach(game::put);
            } else if (Files.exists(saved)) {
                Properties originals = new Properties();
                try (InputStream in = Files.newInputStream(saved)) {
                    originals.load(in);
                }
                for (String key : followerValues.keySet()) {
                    String original = originals.getProperty(key, UNSET);
                    if (UNSET.equals(original)) {
                        game.remove(key);
                    } else {
                        game.put(key, original);
                    }
                }
                Files.delete(saved);
            }
            game.flush();
        } catch (IOException | BackingStoreException failure) {
            System.err.println("[spiral-captain] could not set the " + what + ": " + failure);
        }
    }

    private static Map<String, String> orderedMap(String... keysAndValues) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int index = 0; index < keysAndValues.length; index += 2) {
            map.put(keysAndValues[index], keysAndValues[index + 1]);
        }
        return map;
    }
}

package com.spiralcaptain.app.launch;

import com.spiralcaptain.app.model.Account;
import com.spiralcaptain.app.store.AccountStore;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

public final class PrefsProfile {

    private static final String GAME_NODE = "projectx";

    private static final String SEED_FILE = "seed.xml";

    private static final String OVERRIDES_FILE = "overrides.properties";

    private static final String SEED_MARKER = "seed.imported";

    private static final String STEAM_LOGON = "steam_logon";
    private static final String ANONYMOUS_LOGON = "anonymous_logon";

    private final Path directory;

    private PrefsProfile(Path directory) {
        this.directory = directory;
    }

    public static Path directoryFor(Account account) {
        return AccountStore.defaultDirectory().resolve("prefs").resolve(account.id());
    }

    public static PrefsProfile prepare(Account account, boolean seedFromCurrent) {
        PrefsProfile profile = new PrefsProfile(directoryFor(account));
        profile.createDirectory();
        if (seedFromCurrent) {
            profile.seedOnce();
        }
        profile.writeOverrides(account);
        return profile;
    }

    public Path directory() {
        return directory;
    }

    private void createDirectory() {
        try {
            Files.createDirectories(directory);
        } catch (IOException ioe) {
            throw new AccountStore.StoreException("Cannot create " + directory, ioe);
        }
    }

    private void seedOnce() {
        Path seed = directory.resolve(SEED_FILE);
        if (Files.exists(seed) || Files.exists(directory.resolve(SEED_MARKER))) {
            return;
        }
        try {
            Preferences current = Preferences.userRoot();
            if (!current.nodeExists(GAME_NODE)) {
                return;
            }
            Preferences game = current.node(GAME_NODE);
            try (OutputStream out = Files.newOutputStream(seed)) {
                game.exportSubtree(out);
            }
            stripLoginKeys(seed);
        } catch (IOException | BackingStoreException failure) {
            System.err.println("Could not seed preferences for " + directory + ": " + failure);
            try {
                Files.deleteIfExists(seed);
            } catch (IOException ignored) {
            }
        }
    }

    private static void stripLoginKeys(Path seed) throws IOException {
        String exported = Files.readString(seed);
        StringBuilder kept = new StringBuilder(exported.length());
        for (String line : exported.split("\n", -1)) {
            String trimmed = line.trim();
            boolean isLoginKey = trimmed.startsWith("<entry")
                    && (trimmed.contains("key=\"" + STEAM_LOGON + "\"")
                        || trimmed.contains("key=\"" + ANONYMOUS_LOGON + "\"")
                        || trimmed.contains("key=\"username\""));
            if (!isLoginKey) {
                kept.append(line).append('\n');
            }
        }
        Files.writeString(seed, kept.toString());
    }

    private void writeOverrides(Account account) {
        Properties overrides = new Properties();
        boolean steamLogin = account.loginMode() == Account.LoginMode.STEAM;
        overrides.setProperty(STEAM_LOGON, Boolean.toString(steamLogin));
        overrides.setProperty(ANONYMOUS_LOGON, "false");
        try (OutputStream out = Files.newOutputStream(directory.resolve(OVERRIDES_FILE))) {
            overrides.store(out, "Applied by Spiral Captain on every launch");
        } catch (IOException ioe) {
            throw new AccountStore.StoreException(
                    "Cannot write preference overrides for " + account.displayName(), ioe);
        }
    }
}

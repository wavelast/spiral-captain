package com.spiralcaptain.app.store;

import com.spiralcaptain.app.model.CustomLayout;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class Settings {

    private static final String FILE_NAME = "settings.properties";

    public static final String THEME_SYSTEM = "system";
    public static final String THEME_LIGHT = "light";
    public static final String THEME_DARK = "dark";

    private final Path file;
    private final Properties values = new Properties();

    public Settings() {
        this(AccountStore.defaultDirectory().resolve(FILE_NAME));
    }

    public Settings(Path file) {
        this.file = file;
        load();
    }

    public String gameDirectory() {
        return values.getProperty("gameDirectory", "");
    }

    public void gameDirectory(String directory) {
        values.setProperty("gameDirectory", directory == null ? "" : directory);
    }

    public boolean muteFollowers() {
        return flag("muteFollowers", true);
    }

    public void muteFollowers(boolean mute) {
        values.setProperty("muteFollowers", Boolean.toString(mute));
    }

    public boolean seedPreferences() {
        return flag("seedPreferences", true);
    }

    public String theme() {
        String theme = values.getProperty("theme", THEME_SYSTEM);
        return List.of(THEME_SYSTEM, THEME_LIGHT, THEME_DARK).contains(theme)
                ? theme
                : THEME_SYSTEM;
    }

    public void theme(String theme) {
        values.setProperty("theme", theme == null ? THEME_SYSTEM : theme);
    }

    public boolean lightFollowers() {
        return flag("lightFollowers", true);
    }

    public void lightFollowers(boolean light) {
        values.setProperty("lightFollowers", Boolean.toString(light));
    }

    public boolean lowerFollowerPriority() {
        return flag("lowerFollowerPriority", true);
    }

    public void lowerFollowerPriority(boolean lower) {
        values.setProperty("lowerFollowerPriority", Boolean.toString(lower));
    }

    public boolean checkForUpdates() {
        return flag("checkForUpdates", true);
    }

    public void checkForUpdates(boolean check) {
        values.setProperty("checkForUpdates", Boolean.toString(check));
    }

    public String arrangeMonitor() {
        return values.getProperty("arrangeMonitor", "");
    }

    public void arrangeMonitor(String device) {
        values.setProperty("arrangeMonitor", device == null ? "" : device);
    }

    public String lastLayout() {
        return values.getProperty("lastLayout", "");
    }

    public void lastLayout(String key) {
        values.setProperty("lastLayout", key == null ? "" : key);
    }

    public List<CustomLayout> customLayouts() {
        List<CustomLayout> layouts = new ArrayList<>();
        for (String id : values.getProperty("layouts", "").split(",")) {
            if (id.isBlank()) {
                continue;
            }
            String name = values.getProperty("layout." + id + ".name", "Custom");
            try {
                List<CustomLayout.Slot> slots = CustomLayout.decodeSlots(
                        values.getProperty("layout." + id + ".slots", ""));
                if (!slots.isEmpty()) {
                    layouts.add(new CustomLayout(id, name, slots));
                }
            } catch (RuntimeException unreadable) {
                System.err.println("Skipping unreadable layout " + name + ": " + unreadable);
            }
        }
        return layouts;
    }

    public void customLayouts(List<CustomLayout> layouts) {
        values.keySet().removeIf(key -> key.toString().startsWith("layout."));
        List<String> ids = new ArrayList<>();
        for (CustomLayout layout : layouts) {
            ids.add(layout.id());
            values.setProperty("layout." + layout.id() + ".name", layout.name());
            values.setProperty("layout." + layout.id() + ".slots", layout.encodeSlots());
        }
        values.setProperty("layouts", String.join(",", ids));
    }

    private boolean flag(String key, boolean fallback) {
        String stored = values.getProperty(key);
        return stored == null ? fallback : Boolean.parseBoolean(stored);
    }

    private void load() {
        if (!Files.isReadable(file)) {
            return;
        }
        try (InputStream in = Files.newInputStream(file)) {
            values.load(in);
            if (!values.containsKey("theme")
                    && Boolean.parseBoolean(values.getProperty("darkMode"))) {
                values.setProperty("theme", THEME_DARK);
            }
            values.keySet().removeIf(key -> key.toString().startsWith("hotkey.")
                    || key.toString().equals("autoAcceptInvites")
                    || key.toString().equals("maximizeWindows")
                    || key.toString().equals("isolatePreferences")
                    || key.toString().equals("diagnosticLogging")
                    || key.toString().equals("darkMode"));
        } catch (IOException ioe) {
            System.err.println("Cannot read " + file + ": " + ioe);
        }
    }

    public void save() {
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream out = Files.newOutputStream(file)) {
                values.store(out, "Spiral Captain launcher settings");
            }
        } catch (IOException ioe) {
            throw new AccountStore.StoreException("Cannot write " + file, ioe);
        }
    }
}

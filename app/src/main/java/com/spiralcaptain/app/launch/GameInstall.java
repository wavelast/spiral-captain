package com.spiralcaptain.app.launch;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class GameInstall {

    private static final String GETDOWN = "getdown.txt";
    private static final String EXTRA_ARGS = "extra.txt";
    private static final String APPDIR_TOKEN = "%APPDIR%";

    private static final String PLATFORM = "windows";

    private final Path directory;
    private final List<String> codePaths = new ArrayList<>();
    private final List<String> jvmArgs = new ArrayList<>();
    private String mainClass = "com.threerings.projectx.client.ProjectXApp";
    private String version = "";
    private String latestUrl = "";

    private GameInstall(Path directory) {
        this.directory = directory;
    }

    public static GameInstall at(Path directory) {
        GameInstall install = new GameInstall(directory);
        install.readGetdown();
        install.readExtraArgs();
        return install;
    }

    public static boolean looksValid(Path directory) {
        return directory != null
                && Files.isRegularFile(directory.resolve(GETDOWN))
                && Files.isDirectory(directory.resolve("code"));
    }

    public static List<Path> likelyLocations() {
        List<Path> candidates = new ArrayList<>();
        for (Path library : SteamLibraries.gameLibraries()) {
            candidates.add(library.resolve("steamapps/common/Spiral Knights"));
        }
        candidates.add(Path.of("C:/Program Files (x86)/Steam/steamapps/common/Spiral Knights"));
        candidates.add(Path.of("C:/Program Files/Steam/steamapps/common/Spiral Knights"));
        return candidates.stream().distinct().filter(GameInstall::looksValid).toList();
    }

    public Path directory() {
        return directory;
    }

    public Path javaExecutable(boolean windowless) {
        Path bundled = directory.resolve("java_vm/bin").resolve(windowless ? "javaw.exe" : "java.exe");
        if (Files.isExecutable(bundled)) {
            return bundled;
        }
        return Path.of(System.getProperty("java.home"), "bin",
                windowless ? "javaw.exe" : "java.exe");
    }

    public List<Path> classpath() {
        return codePaths.stream().map(directory::resolve).toList();
    }

    public List<String> jvmArgs() {
        return List.copyOf(jvmArgs);
    }

    public String mainClass() {
        return mainClass;
    }

    public Optional<String> version() {
        return version.isEmpty() ? Optional.empty() : Optional.of(version);
    }

    public Optional<String> latestUrl() {
        return latestUrl.isEmpty() ? Optional.empty() : Optional.of(latestUrl);
    }

    public static Optional<String> versionIn(String getdownText) {
        for (String line : getdownText.split("\\R")) {
            int equals = line.indexOf('=');
            if (equals > 0 && line.substring(0, equals).trim().equals("version")) {
                String value = line.substring(equals + 1).trim();
                return value.isEmpty() ? Optional.empty() : Optional.of(value);
            }
        }
        return Optional.empty();
    }

    public String steamAppId() {
        Path appId = directory.resolve("steam_appid.txt");
        try {
            return Files.isReadable(appId) ? Files.readString(appId).trim() : "99900";
        } catch (IOException ioe) {
            return "99900";
        }
    }

    public Path extraArgsFile() {
        return directory.resolve(EXTRA_ARGS);
    }

    private void readGetdown() {
        for (String line : readLines(directory.resolve(GETDOWN))) {
            int equals = line.indexOf('=');
            if (line.isEmpty() || line.startsWith("#") || equals < 0) {
                continue;
            }
            String key = line.substring(0, equals).trim();
            String value = line.substring(equals + 1).trim();
            switch (key) {
                case "code" -> {
                    String path = forThisPlatform(value);
                    if (path != null) {
                        codePaths.add(path);
                    }
                }
                case "jvmarg" -> {
                    String arg = forThisPlatform(value);
                    if (arg != null) {
                        jvmArgs.add(arg.replace(APPDIR_TOKEN, directory.toString()));
                    }
                }
                case "class" -> mainClass = value;
                case "version" -> version = value;
                case "latest" -> latestUrl = value;
                default -> {
                }
            }
        }
    }

    private static String forThisPlatform(String value) {
        if (!value.startsWith("[")) {
            return value;
        }
        int close = value.indexOf(']');
        if (close < 0) {
            return value;
        }
        String tag = value.substring(1, close).trim().toLowerCase(Locale.ROOT);
        String rest = value.substring(close + 1).trim();
        boolean negated = tag.startsWith("!");
        String platform = negated ? tag.substring(1) : tag;
        boolean matches = platform.contains(PLATFORM);
        return matches != negated ? rest : null;
    }

    private void readExtraArgs() {
        for (String line : readLines(extraArgsFile())) {
            if (!line.isEmpty() && !line.startsWith("#") && !jvmArgs.contains(line)) {
                jvmArgs.add(line);
            }
        }
    }

    private static List<String> readLines(Path file) {
        if (!Files.isReadable(file)) {
            return List.of();
        }
        try {
            List<String> lines = new ArrayList<>();
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                lines.add(line.trim());
            }
            return lines;
        } catch (IOException ioe) {
            return List.of();
        }
    }
}

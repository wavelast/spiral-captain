package com.spiralcaptain.app.launch;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SteamLibraries {

    private static final Pattern REGISTRY_VALUE =
            Pattern.compile("SteamPath\\s+REG_\\w+\\s+(.+)");
    private static final Pattern VDF_PATH =
            Pattern.compile("\"path\"\\s+\"(.+?)\"");

    private SteamLibraries() {
    }

    public static Optional<Path> steamPath() {
        String value = registryValue("HKCU\\Software\\Valve\\Steam", "SteamPath");
        if (value == null) {
            value = registryValue("HKLM\\SOFTWARE\\WOW6432Node\\Valve\\Steam", "InstallPath");
        }
        if (value == null) {
            Path fallback = Path.of("C:/Program Files (x86)/Steam");
            return Files.isDirectory(fallback) ? Optional.of(fallback) : Optional.empty();
        }
        Path path = Path.of(value.trim());
        return Files.isDirectory(path) ? Optional.of(path) : Optional.empty();
    }

    public static List<Path> gameLibraries() {
        Set<Path> libraries = new LinkedHashSet<>();
        steamPath().ifPresent(steam -> {
            libraries.add(steam);
            Path vdf = steam.resolve("steamapps/libraryfolders.vdf");
            if (Files.isReadable(vdf)) {
                try {
                    Matcher matcher = VDF_PATH.matcher(Files.readString(vdf));
                    while (matcher.find()) {
                        Path library = Path.of(matcher.group(1).replace("\\\\", "\\"));
                        if (Files.isDirectory(library)) {
                            libraries.add(library);
                        }
                    }
                } catch (IOException ioe) {
                }
            }
        });
        return new ArrayList<>(libraries);
    }

    private static String registryValue(String key, String name) {
        try {
            Process process = new ProcessBuilder("reg", "query", key, "/v", name)
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes());
            if (!process.waitFor(5, TimeUnit.SECONDS) || process.exitValue() != 0) {
                return null;
            }
            Matcher matcher = name.equals("SteamPath")
                    ? REGISTRY_VALUE.matcher(output)
                    : Pattern.compile(Pattern.quote(name) + "\\s+REG_\\w+\\s+(.+)").matcher(output);
            return matcher.find() ? matcher.group(1).trim() : null;
        } catch (IOException ioe) {
            return null;
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            return null;
        }
    }
}

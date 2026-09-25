package com.spiralcaptain.app.launch;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

public final class PrefsJar {

    private static final String PROPERTY = "spiralcaptain.prefsJar";
    private static final String NAME_PREFIX = "spiral-captain-prefs";

    private PrefsJar() {
    }

    public static Optional<Path> locate() {
        String configured = System.getProperty(PROPERTY);
        if (configured != null && !configured.isBlank()) {
            Path path = Path.of(configured);
            return Files.isRegularFile(path) ? Optional.of(path.toAbsolutePath()) : Optional.empty();
        }
        for (Path directory : searchDirectories()) {
            Optional<Path> found = newestJarIn(directory);
            if (found.isPresent()) {
                return found;
            }
        }
        return Optional.empty();
    }

    public static List<Path> searchDirectories() {
        List<Path> directories = new ArrayList<>();
        launcherDirectory().ifPresent(directory -> {
            directories.add(directory);
            directories.add(directory.resolve("lib"));
            Path repository = directory.getParent() == null
                    ? null
                    : directory.getParent().getParent();
            if (repository != null) {
                directories.add(repository.resolve("prefs/target"));
            }
        });
        directories.add(Path.of("prefs/target").toAbsolutePath());
        return directories.stream().filter(Files::isDirectory).distinct().toList();
    }

    private static Optional<Path> launcherDirectory() {
        try {
            Path self = Path.of(PrefsJar.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            return Optional.of(Files.isDirectory(self) ? self : self.getParent());
        } catch (URISyntaxException | RuntimeException unknown) {
            return Optional.empty();
        }
    }

    private static Optional<Path> newestJarIn(Path directory) {
        try (Stream<Path> entries = Files.list(directory)) {
            return entries
                    .filter(path -> {
                        String name = path.getFileName().toString();
                        return name.startsWith(NAME_PREFIX) && name.endsWith(".jar")
                                && !name.startsWith("original-");
                    })
                    .max((left, right) -> Long.compare(modified(left), modified(right)))
                    .map(Path::toAbsolutePath);
        } catch (IOException ioe) {
            return Optional.empty();
        }
    }

    private static long modified(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException ioe) {
            return 0L;
        }
    }
}

package com.spiralcaptain.app.launch;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public final class GameUpdates {

    public enum State {

        UP_TO_DATE,

        UPDATE_AVAILABLE,

        UNKNOWN
    }

    public record Check(State state, String installed, String latest, String problem) {

        static Check unknown(String installed, String problem) {
            return new Check(State.UNKNOWN, installed, "", problem);
        }
    }

    private static final Duration TIMEOUT = Duration.ofSeconds(6);
    private static final String UPDATER = "getdown-pro.jar";
    private static final long UPDATE_LIMIT_MINUTES = 30;

    private GameUpdates() {
    }

    public static Check check(GameInstall install) {
        String installed = install.version().orElse("");
        Optional<String> latestUrl = install.latestUrl();
        if (installed.isEmpty() || latestUrl.isEmpty()) {
            return Check.unknown(installed, "the game folder does not say which version it is");
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(TIMEOUT)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();
            HttpRequest request = HttpRequest.newBuilder(URI.create(latestUrl.get()))
                    .timeout(TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Check.unknown(installed,
                        "the update server answered " + response.statusCode());
            }
            Optional<String> latest = GameInstall.versionIn(response.body());
            if (latest.isEmpty()) {
                return Check.unknown(installed, "the update server did not say which version is "
                        + "the latest");
            }
            State state = newer(latest.get(), installed)
                    ? State.UPDATE_AVAILABLE
                    : State.UP_TO_DATE;
            return new Check(state, installed, latest.get(), "");
        } catch (IOException | IllegalArgumentException failure) {
            return Check.unknown(installed, "the update server could not be reached");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Check.unknown(installed, "the check was cancelled");
        }
    }

    static boolean newer(String latest, String installed) {
        try {
            return Long.parseLong(latest) > Long.parseLong(installed);
        } catch (NumberFormatException notNumbers) {
            return !latest.equals(installed);
        }
    }

    public static void update(GameInstall install) throws IOException, InterruptedException {
        Path updater = install.directory().resolve(UPDATER);
        if (!Files.isRegularFile(updater)) {
            throw new IOException(UPDATER + " is missing from the game folder");
        }
        List<String> command = List.of(
                install.javaExecutable(true).toString(),
                "-Dsun.java2d.d3d=false",
                "-Dcheck_unpacked=true",
                "-Dsilent=true",
                "-jar", UPDATER, ".", "client");
        Process process = new ProcessBuilder(command)
                .directory(install.directory().toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .start();
        if (!process.waitFor(UPDATE_LIMIT_MINUTES, TimeUnit.MINUTES)) {
            process.destroy();
            throw new IOException("the updater was still running after "
                    + UPDATE_LIMIT_MINUTES + " minutes");
        }
    }
}

package com.spiralcaptain.app;

import com.spiralcaptain.app.launch.PrefsJar;
import com.spiralcaptain.app.launch.ClientLauncher;
import com.spiralcaptain.app.launch.GameInstall;
import com.spiralcaptain.app.launch.SteamLibraries;
import com.spiralcaptain.app.model.Account;
import com.spiralcaptain.app.store.AccountStore;
import com.spiralcaptain.app.store.Secrets;
import com.spiralcaptain.app.store.Settings;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class Doctor {

    private static int problems;

    private Doctor() {
    }

    public static void main(String[] args) {
        System.out.println("Spiral Captain setup check");
        System.out.println();

        checkGame();
        checkPrefs();
        checkSecrets();
        checkStorage();

        System.out.println();
        System.out.println(problems == 0
                ? "Everything needed to launch is in place."
                : problems + " thing(s) need attention before launching.");
        if (problems > 0) {
            System.exit(1);
        }
    }

    private static void checkGame() {
        System.out.println("Game install");
        Settings settings = new Settings();
        Path configured = settings.gameDirectory().isEmpty()
                ? null : Path.of(settings.gameDirectory());
        Path directory = configured != null && GameInstall.looksValid(configured)
                ? configured
                : GameInstall.likelyLocations().stream().findFirst().orElse(null);
        if (directory == null) {
            fail("  no Spiral Knights install found; set the game folder in the launcher");
            SteamLibraries.steamPath().ifPresent(steam ->
                    System.out.println("  Steam itself is at " + steam));
            return;
        }
        ok("  found at " + directory);
        GameInstall install = GameInstall.at(directory);
        List<Path> classpath = install.classpath();
        ok("  main class " + install.mainClass());
        ok("  installed version " + install.version().orElse("unknown")
                + (install.latestUrl().isPresent() ? "" : " (no update address in getdown.txt)"));
        ok("  " + classpath.size() + " jars on the game classpath");
        long missing = classpath.stream().filter(jar -> !Files.isReadable(jar)).count();
        if (missing > 0) {
            fail("  " + missing + " of them are missing; run the game's own launcher once to "
                    + "let it finish updating");
            classpath.stream().filter(jar -> !Files.isReadable(jar))
                    .forEach(jar -> System.out.println("      missing " + jar));
        }
        Path java = install.javaExecutable(true);
        if (Files.isExecutable(java)) {
            ok("  game JVM at " + java);
        } else {
            fail("  no JVM found at " + java);
        }
        ok("  Steam app id " + install.steamAppId());
        ok("  JVM arguments taken from getdown.txt and extra.txt:");
        install.jvmArgs().forEach(arg -> System.out.println("      " + arg));
        if (install.jvmArgs().stream().noneMatch(arg -> arg.startsWith("-Dappdir="))) {
            fail("  no -Dappdir argument; the client would not find its resources");
        }
    }

    private static void checkPrefs() {
        System.out.println();
        System.out.println("Per-account game settings");
        PrefsJar.locate().ifPresentOrElse(
                jar -> ok("  " + jar),
                () -> fail("  spiral-captain-prefs.jar not found; looked in "
                        + PrefsJar.searchDirectories()));
    }

    private static void checkSecrets() {
        System.out.println();
        System.out.println("Password handling");
        String expected = "098f6bcd4621d373cade4e832627b4f6";
        String actual = Secrets.digest("test");
        if (expected.equals(actual)) {
            ok("  digest matches the game's own hashing");
        } else {
            fail("  digest mismatch: expected " + expected + ", got " + actual);
        }
        if (Secrets.encryptionAvailable()) {
            String sealed = Secrets.seal(actual);
            String recovered = Secrets.unseal(sealed);
            if (actual.equals(recovered)) {
                ok("  Windows encryption works (stored form starts \""
                        + sealed.substring(0, Math.min(12, sealed.length())) + "...\")");
            } else {
                fail("  Windows encryption did not round trip");
            }
        } else {
            fail("  Windows encryption unavailable (" + Secrets.unavailableReason()
                    + "); digests would be stored unencrypted");
        }
    }

    private static void checkStorage() {
        System.out.println();
        System.out.println("Storage");
        AccountStore store = new AccountStore();
        ok("  accounts file " + store.file());
        List<Account> accounts = store.load();
        ok("  " + accounts.size() + " account(s) saved");
        for (Account account : accounts) {
            String problem = account.launchProblem();
            String detail = "    " + account.displayName()
                    + " [" + account.loginMode() + (account.main() ? ", main" : "") + "]";
            if (problem == null) {
                ok(detail);
            } else {
                fail(detail + " -- needs " + problem);
            }
        }
        printLaunchCommand(accounts);
    }

    private static void printLaunchCommand(List<Account> accounts) {
        Settings settings = new Settings();
        Path directory = settings.gameDirectory().isEmpty()
                ? GameInstall.likelyLocations().stream().findFirst().orElse(null)
                : Path.of(settings.gameDirectory());
        if (directory == null) {
            return;
        }
        Account sample = accounts.stream().findFirst().orElseGet(() -> {
            Account example = new Account();
            example.accountName("example");
            example.knightName("ExampleKnight");
            example.passwordDigest("0".repeat(32));
            return example;
        });
        System.out.println();
        System.out.println("Launch command for " + sample.displayName());
        ClientLauncher launcher =
                new ClientLauncher(GameInstall.at(directory), settings,
                        PrefsJar.locate().orElse(null));
        System.out.println("  " + launcher.describeCommand(sample));
    }

    private static void ok(String line) {
        System.out.println(line);
    }

    private static void fail(String line) {
        System.out.println("  !!" + line);
        problems++;
    }
}

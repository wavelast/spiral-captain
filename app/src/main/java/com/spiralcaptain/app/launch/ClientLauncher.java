package com.spiralcaptain.app.launch;

import com.spiralcaptain.app.model.Account;
import com.spiralcaptain.app.store.Settings;
import com.spiralcaptain.common.LaunchKeys;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ClientLauncher {

    private final GameInstall install;
    private final Settings settings;
    private final Path prefsJar;

    public ClientLauncher(GameInstall install, Settings settings, Path prefsJar) {
        this.install = install;
        this.settings = settings;
        this.prefsJar = prefsJar;
    }

    public Process launch(Account account) throws IOException {
        List<String> command = buildCommand(account, true);
        ProcessBuilder builder = new ProcessBuilder(command)
                .directory(install.directory().toFile())
                .redirectErrorStream(true)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD);
        return builder.start();
    }

    public String describeCommand(Account account) {
        return String.join("\n  ", buildCommand(account, false));
    }

    private boolean isolated() {
        return prefsJar != null;
    }

    private List<String> buildCommand(Account account, boolean prepare) {
        List<String> command = new ArrayList<>();
        command.add(install.javaExecutable(true).toString());

        command.add("-classpath");
        command.add(classpath());

        command.addAll(install.jvmArgs());
        command.add("-Dcom.threerings.getdown=true");
        command.add("-Dno_log_redir=true");

        if (isolated()) {
            Path prefsDirectory = prepare
                    ? PrefsProfile.prepare(account, settings.seedPreferences()).directory()
                    : PrefsProfile.directoryFor(account);
            command.add(define("java.util.prefs.PreferencesFactory",
                    "com.spiralcaptain.prefs.FilePreferencesFactory"));
            command.add(define(LaunchKeys.PREFS_DIR, prefsDirectory.toString()));
            command.add(define(LaunchKeys.MAIN, Boolean.toString(account.main())));
            if (settings.muteFollowers()) {
                command.add(define(LaunchKeys.MUTE_FOLLOWERS, "true"));
            }
            if (settings.lightFollowers()) {
                command.add(define(LaunchKeys.LIGHT_FOLLOWERS, "true"));
            }
        }

        if (account.loginMode() == Account.LoginMode.PASSWORD) {
            command.add(define(LaunchKeys.GAME_USERNAME, account.accountName()));
            command.add(define(LaunchKeys.GAME_PASSWORD,
                    prepare ? account.passwordDigest() : "<digest withheld>"));
            command.add(define(LaunchKeys.GAME_ENCRYPTED, "true"));
        }
        if (!account.knightName().isEmpty()) {
            command.add(define(LaunchKeys.GAME_KNIGHT, account.knightName()));
        }

        command.add(install.mainClass());
        return command;
    }

    private String classpath() {
        List<String> entries = new ArrayList<>();
        if (isolated()) {
            entries.add(prefsJar.toString());
        }
        entries.addAll(install.classpath().stream().map(Path::toString).toList());
        return String.join(";", entries);
    }

    private static String define(String key, String value) {
        return "-D" + key + "=" + value;
    }
}

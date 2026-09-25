package com.spiralcaptain.app.ui;

import com.spiralcaptain.app.control.Fleet;
import com.spiralcaptain.app.launch.PrefsJar;
import com.spiralcaptain.app.launch.GameInstall;
import com.spiralcaptain.app.model.Account;
import com.spiralcaptain.app.store.AccountStore;
import com.spiralcaptain.app.store.Settings;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class AppState {

    private final AccountStore store = new AccountStore();
    private final Settings settings = new Settings();
    private final ObservableList<AccountRow> rows = FXCollections.observableArrayList();
    private final List<Runnable> refreshers = new ArrayList<>();
    private final StringProperty status = new SimpleStringProperty("Ready");

    private GameInstall install;
    private Path prefsJar;
    private Fleet fleet;
    private Consumer<String> alerts = message -> { };

    public AppState() {
        for (Account account : store.load()) {
            rows.add(watch(new AccountRow(account, this::save)));
        }
        steamRow().ifPresent(steam -> {
            rows.stream().filter(row -> row != steam)
                    .forEach(row -> row.steamProperty().set(false));
            place(steam, 0);
        });
        resolveInstall();
        if (install != null) {
            fleet = new Fleet(settings, install, prefsJar);
            fleet.onChange(() -> Platform.runLater(this::refresh));
        }
    }

    public Settings settings() {
        return settings;
    }

    public ObservableList<AccountRow> rows() {
        return rows;
    }

    public List<Account> accounts() {
        return rows.stream().map(AccountRow::account).toList();
    }

    public GameInstall install() {
        return install;
    }

    public Fleet fleet() {
        return fleet;
    }

    public StringProperty status() {
        return status;
    }

    public void alerts(Consumer<String> alerts) {
        this.alerts = alerts;
    }

    public void alert(String message) {
        if (message != null && !message.isEmpty()) {
            alerts.accept(message);
        }
    }

    public void onRefresh(Runnable refresher) {
        refreshers.add(refresher);
    }

    public void refresh() {
        for (AccountRow row : rows) {
            row.showClient(fleet == null ? null : fleet.clientFor(row.account()).orElse(null));
        }
        if (fleet != null) {
            List<String> log = fleet.recentLog();
            if (!log.isEmpty()) {
                status.set(log.getLast());
            }
        }
        refreshers.forEach(Runnable::run);
    }

    public AccountRow addAccount() {
        Account account = new Account();
        account.accountName("new account");
        AccountRow row = watch(new AccountRow(account, this::save));
        rows.add(row);
        orderChanged();
        return row;
    }

    public Optional<AccountRow> addSteamAccount() {
        if (steamRow().isPresent()) {
            return Optional.empty();
        }
        Account account = new Account();
        account.accountName("Steam account");
        account.loginMode(Account.LoginMode.STEAM);
        AccountRow row = watch(new AccountRow(account, this::save));
        rows.addFirst(row);
        orderChanged();
        return Optional.of(row);
    }

    public void remove(AccountRow row) {
        rows.remove(row);
        orderChanged();
    }

    public Optional<AccountRow> steamRow() {
        return rows.stream().filter(row -> row.steamProperty().get()).findFirst();
    }

    public boolean moveTo(AccountRow row, int position) {
        Optional<AccountRow> steam = steamRow();
        if (steam.isPresent()) {
            if (row == steam.get()) {
                return false;
            }
            if (position < 1) {
                place(row, 1);
                return false;
            }
        }
        place(row, position);
        return true;
    }

    private void place(AccountRow row, int position) {
        int from = rows.indexOf(row);
        int to = Math.max(0, Math.min(rows.size() - 1, position));
        if (from < 0 || from == to) {
            return;
        }
        rows.remove(from);
        rows.add(to, row);
        orderChanged();
    }

    private AccountRow watch(AccountRow row) {
        row.steamProperty().addListener((source, was, now) -> {
            if (!now) {
                save();
                refresh();
                return;
            }
            rows.stream().filter(other -> other != row)
                    .forEach(other -> other.steamProperty().set(false));
            place(row, 0);
            save();
        });
        return row;
    }

    private void orderChanged() {
        Account previousMain = accounts().stream().filter(Account::main).findFirst().orElse(null);
        List<Account> ordered = accounts();
        for (int index = 0; index < ordered.size(); index++) {
            ordered.get(index).main(index == 0);
        }
        save();
        Account newMain = ordered.isEmpty() ? null : ordered.getFirst();
        if (fleet != null && newMain != null && newMain != previousMain) {
            fleet.makeMain(newMain);
        }
        refresh();
    }

    public void save() {
        try {
            store.save(accounts());
        } catch (RuntimeException failure) {
            alert("Could not save accounts: " + failure.getMessage());
        }
    }

    public List<Account> checkedAccounts() {
        return accounts().stream().filter(Account::selected).toList();
    }

    private void resolveInstall() {
        Path directory = settings.gameDirectory().isEmpty()
                ? GameInstall.likelyLocations().stream().findFirst().orElse(null)
                : Path.of(settings.gameDirectory());
        if (directory != null && GameInstall.looksValid(directory)) {
            install = GameInstall.at(directory);
            if (settings.gameDirectory().isEmpty()) {
                settings.gameDirectory(directory.toString());
                settings.save();
            }
        }
        prefsJar = PrefsJar.locate().orElse(null);
    }

    public String installProblem() {
        if (install == null) {
            return "Spiral Knights was not found. Set the game folder in Settings.";
        }
        if (prefsJar == null) {
            return "spiral-captain-prefs.jar was not found, so each account cannot have its "
                    + "own game settings. Looked in: " + PrefsJar.searchDirectories();
        }
        return "";
    }

    public void shutdown() {
        save();
        if (fleet != null) {
            fleet.shutdown();
        }
    }
}

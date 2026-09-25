package com.spiralcaptain.app.ui;

import com.spiralcaptain.app.control.RunningClient;
import com.spiralcaptain.app.model.Account;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;

public final class AccountRow {

    private final Account account;

    private final BooleanProperty selected = new SimpleBooleanProperty();
    private final StringProperty accountName = new SimpleStringProperty();
    private final StringProperty knightName = new SimpleStringProperty();
    private final BooleanProperty steam = new SimpleBooleanProperty();

    private final StringProperty status = new SimpleStringProperty("Not running");

    public AccountRow(Account account, Runnable onEdit) {
        this.account = account;
        pullFromAccount();

        selected.addListener((source, was, now) -> {
            account.selected(now);
            onEdit.run();
        });
        accountName.addListener((source, was, now) -> {
            account.accountName(now);
            onEdit.run();
        });
        knightName.addListener((source, was, now) -> {
            account.knightName(now);
            onEdit.run();
        });
        steam.addListener((source, was, now) -> {
            account.loginMode(now ? Account.LoginMode.STEAM : Account.LoginMode.PASSWORD);
            onEdit.run();
        });
    }

    public Account account() {
        return account;
    }

    public BooleanProperty selectedProperty() {
        return selected;
    }

    public StringProperty accountNameProperty() {
        return accountName;
    }

    public StringProperty knightNameProperty() {
        return knightName;
    }

    public BooleanProperty steamProperty() {
        return steam;
    }

    public StringProperty statusProperty() {
        return status;
    }

    private void pullFromAccount() {
        selected.set(account.selected());
        accountName.set(account.accountName());
        knightName.set(account.knightName());
        steam.set(account.loginMode() == Account.LoginMode.STEAM);
    }

    public void showClient(RunningClient client) {
        if (client == null || client.state() == RunningClient.State.STOPPED) {
            status.set(account.launchProblem() == null
                    ? "Not running"
                    : "Needs " + account.launchProblem());
            return;
        }
        status.set(client.state().toString());
    }
}

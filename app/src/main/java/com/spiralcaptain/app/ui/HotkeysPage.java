package com.spiralcaptain.app.ui;

import com.spiralcaptain.app.model.Account;
import com.spiralcaptain.app.model.Hotkey;
import com.spiralcaptain.app.store.Settings;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class HotkeysPage extends ScrollPane {

    private final AppState state;
    private final Settings settings;
    private final GridPane rows = new GridPane();
    private final List<HotkeyField> fields = new ArrayList<>();
    private final Label noAccounts = Pages.hint("Add an account to give it a hotkey.");
    private List<String> shownNames;

    public HotkeysPage(AppState state) {
        this.state = state;
        this.settings = state.settings();
        getStyleClass().add("page-scroll");
        setFitToWidth(true);

        VBox content = new VBox(14,
                Pages.header("Hotkeys", "Bring a client's game window to the front with a key "
                        + "combination, from the game or from any other program."),
                switchingCard(),
                accountsCard());
        content.getStyleClass().add("page");
        setContent(content);

        state.onRefresh(this::refresh);
        parentProperty().addListener((source, was, now) -> refresh());
        refresh();
    }

    private VBox switchingCard() {
        CheckBox enabled = new CheckBox("Switch to a client's window with a hotkey");
        enabled.setSelected(settings.windowHotkeys());
        rows.setDisable(!enabled.isSelected());
        enabled.setOnAction(event -> {
            settings.windowHotkeys(enabled.isSelected());
            settings.save();
            rows.setDisable(!enabled.isSelected());
            state.hotkeysChanged();
        });
        Pages.describe(enabled, "While clients are running, an account's hotkey brings its game "
                + "window to the front, from the game or from any other program. It only "
                + "switches windows, the same as clicking the window on the taskbar: the keys go "
                + "to the launcher, and nothing is sent to the game. While clients are running, "
                + "other programs cannot use these key combinations.");
        return Pages.card("Window Switching", enabled);
    }

    private VBox accountsCard() {
        rows.setHgap(12);
        rows.setVgap(8);
        rows.setAlignment(Pos.CENTER_LEFT);
        rows.managedProperty().bind(rows.visibleProperty());
        noAccounts.managedProperty().bind(noAccounts.visibleProperty());
        return Pages.card("Account Hotkeys", "Numbers follow the account list, so #1 is the main "
                + "account. Click a hotkey, then press the keys to use instead. Esc keeps the old "
                + "one, and Backspace or Delete leaves that account without one.",
                rows, noAccounts);
    }

    private void refresh() {
        List<Account> accounts = state.accounts();
        List<String> names = accounts.stream().map(Account::displayName).toList();
        if (!names.equals(shownNames)) {
            shownNames = names;
            rows.getChildren().clear();
            fields.clear();
            for (int index = 0; index < names.size(); index++) {
                int number = index + 1;
                Label position = new Label("#" + number);
                position.getStyleClass().add("hotkey-number");
                Label name = new Label(names.get(index));
                HotkeyField field = new HotkeyField(hotkey -> assign(number, hotkey),
                        state::suspendHotkeys);
                rows.addRow(index, position, name, field);
                fields.add(field);
            }
        }
        for (int index = 0; index < fields.size(); index++) {
            fields.get(index).show(settings.windowHotkey(index + 1));
        }
        noAccounts.setVisible(accounts.isEmpty());
        rows.setVisible(!accounts.isEmpty());
    }

    private void assign(int number, Hotkey hotkey) {
        int count = state.accounts().size();
        String movedFrom = null;
        if (hotkey != null) {
            for (int other = 1; other <= Math.max(count, Hotkey.DEFAULT_COUNT); other++) {
                if (other != number && settings.windowHotkey(other).equals(Optional.of(hotkey))) {
                    settings.windowHotkey(other, null);
                    if (other <= count) {
                        movedFrom = "#" + other;
                    }
                }
            }
        }
        settings.windowHotkey(number, hotkey);
        settings.save();
        if (movedFrom != null) {
            state.status().set(hotkey.text() + " now switches to #" + number + ", so "
                    + movedFrom + " has no hotkey");
        }
        refresh();
        state.hotkeysChanged();
    }
}

package com.spiralcaptain.app.ui;

import com.spiralcaptain.app.launch.GameInstall;
import com.spiralcaptain.app.store.Settings;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.util.StringConverter;

import java.io.File;

public final class SettingsPage extends ScrollPane {

    private final AppState state;
    private final Settings settings;

    public SettingsPage(AppState state) {
        this.state = state;
        this.settings = state.settings();
        getStyleClass().add("page-scroll");
        setFitToWidth(true);

        VBox content = new VBox(14,
                Pages.header("Settings", "Where the game lives, and how the clients start."),
                gameCard(),
                appearanceCard(),
                launchCard());
        content.getStyleClass().add("page");
        setContent(content);
    }

    private VBox gameCard() {
        GameInstall install = state.install();
        Label path = new Label(install == null ? "Not found" : install.directory().toString());
        path.getStyleClass().add("path-label");
        path.setWrapText(true);
        HBox.setHgrow(path, Priority.ALWAYS);
        path.setMaxWidth(Double.MAX_VALUE);

        Button change = Pages.button("Change", null);
        change.setOnAction(event -> chooseGameFolder());

        HBox row = new HBox(10, path, change);
        row.setAlignment(Pos.CENTER_LEFT);
        return Pages.card("Game Location", "Found automatically through Steam. Change it if the "
                + "game lives somewhere else.", row);
    }

    private VBox appearanceCard() {
        ComboBox<String> theme = new ComboBox<>(FXCollections.observableArrayList(
                Settings.THEME_SYSTEM, Settings.THEME_LIGHT, Settings.THEME_DARK));
        theme.setConverter(new StringConverter<>() {
            @Override
            public String toString(String value) {
                if (value == null) {
                    return "";
                }
                return switch (value) {
                    case Settings.THEME_LIGHT -> "Light";
                    case Settings.THEME_DARK -> "Dark";
                    default -> "Match Windows";
                };
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        theme.setValue(settings.theme());
        theme.valueProperty().addListener((source, was, now) -> {
            settings.theme(now);
            settings.save();
            Theme.setDark(getScene(), Theme.darkFor(now));
        });
        Label label = new Label("Theme");
        HBox row = new HBox(10, label, theme);
        row.setAlignment(Pos.CENTER_LEFT);
        return Pages.card("Appearance", "Match Windows follows the light or dark mode set in "
                + "Windows, and switches along with it.", row);
    }

    private VBox launchCard() {
        CheckBox updates = new CheckBox("Check for game updates when the launcher opens");
        updates.setSelected(settings.checkForUpdates());
        updates.setOnAction(event -> {
            settings.checkForUpdates(updates.isSelected());
            settings.save();
        });
        Pages.describe(updates, "Launching starts the game directly, skipping the step where "
                + "the game's own launcher updates it. With this on, the launcher asks the "
                + "game's update server for the latest version when it opens, and if a newer one "
                + "is out, shows a message in the middle of the window offering to update now or "
                + "later, since clients from an older version may not be able to log in.");

        CheckBox mute = new CheckBox("Mute followers");
        mute.setSelected(settings.muteFollowers());
        mute.setOnAction(event -> {
            settings.muteFollowers(mute.isSelected());
            settings.save();
        });
        Pages.describe(mute, "Followers start with their in-game music, effects and interface "
                + "volume at zero, in their own settings, so only the main client plays sound. "
                + "Each follower's own levels are kept and come back when that account is "
                + "launched as the main client, or with this turned off. Applies to clients "
                + "launched afterwards.");

        CheckBox light = new CheckBox("Low graphics followers");
        light.setSelected(settings.lightFollowers());
        light.setOnAction(event -> {
            settings.lightFollowers(light.isSelected());
            settings.save();
        });
        Pages.describe(light, "Followers start with the game capped at 30 frames a second, low "
                + "render quality, extra effects off and no antialiasing, in their own settings, "
                + "so the main client gets more of the computer. Each follower's own graphics "
                + "settings are kept and come back when that account is launched as the main "
                + "client, or with this turned off. Applies to clients launched afterwards.");

        CheckBox priority = new CheckBox("Lower follower CPU priority");
        priority.setSelected(settings.lowerFollowerPriority());
        priority.setOnAction(event -> {
            settings.lowerFollowerPriority(priority.isSelected());
            settings.save();
            if (state.fleet() != null) {
                state.fleet().prioritiesChanged();
            }
        });
        Pages.describe(priority, "Followers run at Windows' below normal priority, so when the "
                + "computer is busy the main client is served first. It is a Windows setting "
                + "on each follower's process, not a game setting, and it follows the main "
                + "client straight away, including for clients already running.");
        return Pages.card("Launching", updates, mute, light, priority);
    }

    private void chooseGameFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Where is Spiral Knights installed?");
        File current = new File(settings.gameDirectory());
        if (current.isDirectory()) {
            chooser.setInitialDirectory(current);
        }
        File chosen = chooser.showDialog(getScene().getWindow());
        if (chosen == null) {
            return;
        }
        if (!GameInstall.looksValid(chosen.toPath())) {
            state.alert("That folder has no getdown.txt and no code folder, so it does not look "
                    + "like a Spiral Knights install.");
            return;
        }
        settings.gameDirectory(chosen.getAbsolutePath());
        settings.save();
        state.alert("Game folder saved. Restart the launcher to use it.");
    }
}

package com.spiralcaptain.app.ui;

import com.spiralcaptain.app.store.Settings;
import javafx.application.ColorScheme;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.Pane;
import javafx.scene.shape.SVGPath;

public final class Theme {

    private static final String STYLESHEET = "/spiral-captain.css";
    private static final String DARK = "dark";
    private static final double ICON_SIZE = 20;

    private Theme() {
    }

    public static boolean darkFor(String theme) {
        return switch (theme) {
            case Settings.THEME_DARK -> true;
            case Settings.THEME_LIGHT -> false;
            default -> Platform.getPreferences().getColorScheme() == ColorScheme.DARK;
        };
    }

    public static void apply(Scene scene, boolean darkMode) {
        scene.getStylesheets().add(stylesheet());
        setDark(scene, darkMode);
    }

    public static void setDark(Scene scene, boolean darkMode) {
        mark(scene.getRoot(), darkMode);
    }

    private static void mark(Parent root, boolean darkMode) {
        root.getStyleClass().remove(DARK);
        if (darkMode) {
            root.getStyleClass().add(DARK);
        }
    }

    private static String stylesheet() {
        return Theme.class.getResource(STYLESHEET).toExternalForm();
    }

    public static Node icon(Icon which) {
        SVGPath path = new SVGPath();
        path.setContent(which.path);
        path.getStyleClass().add("icon");
        Pane box = new Pane(path);
        box.setMinSize(ICON_SIZE, ICON_SIZE);
        box.setPrefSize(ICON_SIZE, ICON_SIZE);
        box.setMaxSize(ICON_SIZE, ICON_SIZE);
        box.getStyleClass().add("icon-box");
        return box;
    }

    public enum Icon {
        ACCOUNTS("M7 8a3 3 0 1 1 0-6 3 3 0 0 1 0 6zm0 1.5c-3 0-6 1.5-6 4V16h12v-2.5c0-2.5-3-4-6-4z"
                + "M14 8.2a2.6 2.6 0 1 0 0-5.2 2.6 2.6 0 0 0 0 5.2zm.6 1.4c-.5 0-1 .1-1.4.2"
                + " 1.1.9 1.8 2.1 1.8 3.7V16h4v-2.4c0-2.3-2.3-4-4.4-4z"),
        ARRANGE("M1 2h8v7H1zM11 2h8v7h-8zM1 11h8v7H1zM11 11h8v7h-8z"),
        SETTINGS("M10 6.5a3.5 3.5 0 1 0 0 7 3.5 3.5 0 0 0 0-7zm8 5v-3l-2.2-.5a6 6 0 0 0-.7-1.6"
                + "l1.2-1.9-2.1-2.1-1.9 1.2a6 6 0 0 0-1.6-.7L10.5 1h-3l-.5 2.2a6 6 0 0 0-1.6.7"
                + "L3.5 2.7 1.4 4.8l1.2 1.9a6 6 0 0 0-.7 1.6L-.3 8.5v3l2.2.5c.2.6.4 1.1.7 1.6"
                + "l-1.2 1.9 2.1 2.1 1.9-1.2c.5.3 1 .5 1.6.7l.5 2.2h3l.5-2.2c.6-.2 1.1-.4 1.6-.7"
                + "l1.9 1.2 2.1-2.1-1.2-1.9c.3-.5.5-1 .7-1.6z"),
        LAUNCH("M2 2.2Q2 0 3.9 1.1L16.9 8.6Q18.6 9.6 16.9 10.6L3.9 18.1Q2 19.2 2 17z"),
        DROPDOWN("M4.5 7.5h11L10 14z");

        private final String path;

        Icon(String path) {
            this.path = path;
        }
    }
}

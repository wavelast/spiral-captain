package com.spiralcaptain.app.ui;

import com.spiralcaptain.app.launch.GameWindows;
import com.spiralcaptain.app.model.Arrangement;
import com.spiralcaptain.app.model.CustomLayout;
import com.spiralcaptain.app.model.Layout;
import com.spiralcaptain.app.model.Screen;
import com.spiralcaptain.app.store.Settings;
import com.spiralcaptain.common.Placement;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import javafx.util.StringConverter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ArrangePage extends VBox {

    private static final String MAIN_CLIENT_MONITOR = "";
    private static final int FALLBACK_WIDTH = 1920;
    private static final int FALLBACK_HEIGHT = 1040;
    private static final double PREVIEW_WIDTH = 150;
    private static final double TILE_WIDTH = 184;
    private static final int SLOT_COLOURS = 4;
    private static final double HEADING_HEIGHT = 28;

    private final AppState state;
    private final Settings settings;
    private final ComboBox<String> monitor = new ComboBox<>();
    private final VBox monitorCard;
    private final Map<String, GameWindows.Monitor> monitors = new LinkedHashMap<>();
    private final FlowPane layoutTiles = tiles();
    private final FlowPane customTiles = tiles();
    private final Label noCustom = Pages.hint("No custom layouts yet. Place the game windows "
            + "where you want them, then press Save current windows.");
    private List<CustomLayout> customLayouts;

    public ArrangePage(AppState state) {
        this.state = state;
        this.settings = state.settings();
        this.customLayouts = new ArrayList<>(settings.customLayouts());
        getStyleClass().add("page");
        setSpacing(14);

        monitorCard = Pages.card("Monitor", monitor);
        monitorCard.managedProperty().bind(monitorCard.visibleProperty());
        loadMonitors();
        monitor.setConverter(new StringConverter<>() {
            @Override
            public String toString(String device) {
                return describe(device);
            }

            @Override
            public String fromString(String text) {
                return null;
            }
        });
        monitor.setOnShowing(event -> loadMonitors());
        monitor.valueProperty().addListener((source, was, now) -> {
            if (now != null && !now.equals(settings.arrangeMonitor())) {
                settings.arrangeMonitor(now);
                settings.save();
            }
            rebuildTiles();
        });
        Pages.describe(monitor, "Which monitor the windows are arranged on. The main client's "
                + "monitor follows the main client's window wherever it is.");

        Button save = Pages.button("Save current windows", null);
        save.setOnAction(event -> saveCurrentWindows());
        noCustom.managedProperty().bind(noCustom.visibleProperty());

        getChildren().addAll(
                Pages.header("Arrange", "Moves and sizes the running game windows. The main "
                        + "client takes the first place; the others take the rest in the order "
                        + "they were launched. The last layout you arranged with is outlined "
                        + "and used again by itself as each client's window opens."),
                monitorCard,
                Pages.card("Layouts", "A maximized window is restored first; a client in full screen "
                                + "is left alone. No window is made smaller than the game's own "
                                + "minimum of 1024x600, so on a smaller monitor the windows "
                                + "overlap. The game remembers a window's size but not its place, "
                                + "so arrange again after launching.",
                        layoutTiles),
                Pages.card("Custom Layouts", "Place the game windows by hand, then save them as "
                                + "a layout. Each place is kept as a share of the monitor, so a "
                                + "layout also fits a monitor of another size. A window that was "
                                + "maximized is maximized again. Double-click a layout's name to "
                                + "rename it.",
                        save, noCustom, customTiles));
        rebuildTiles();
        sceneProperty().addListener((source, was, now) -> {
            if (now != null) {
                loadMonitors();
                rebuildTiles();
            }
        });
        javafx.stage.Screen.getScreens().addListener(
                (ListChangeListener<javafx.stage.Screen>) change -> Platform.runLater(() -> {
                    loadMonitors();
                    rebuildTiles();
                }));
    }

    private static FlowPane tiles() {
        FlowPane pane = new FlowPane(14, 14);
        pane.managedProperty().bind(pane.visibleProperty());
        return pane;
    }

    private void loadMonitors() {
        String chosen = monitor.getValue() != null ? monitor.getValue() : settings.arrangeMonitor();
        monitors.clear();
        try {
            GameWindows.monitors().forEach(found -> monitors.put(found.device(), found));
        } catch (RuntimeException unavailable) {
            monitors.clear();
        }
        List<String> choices = new ArrayList<>();
        choices.add(MAIN_CLIENT_MONITOR);
        choices.addAll(monitors.keySet());
        if (!chosen.isEmpty() && !monitors.containsKey(chosen)) {
            choices.add(chosen);
        }
        monitor.setItems(FXCollections.observableArrayList(choices));
        monitor.setValue(chosen);
        monitorCard.setVisible(monitors.size() > 1);
    }

    private String chosenMonitor() {
        return monitors.size() > 1 && monitor.getValue() != null
                ? monitor.getValue()
                : MAIN_CLIENT_MONITOR;
    }

    private String describe(String device) {
        if (device == null || device.isEmpty()) {
            return "Main client's monitor";
        }
        GameWindows.Monitor found = monitors.get(device);
        if (found == null) {
            return device.replace("\\\\.\\", "") + " (not connected)";
        }
        return "Display " + found.number() + "  ·  " + found.width() + " × " + found.height()
                + (found.primary() ? "  ·  Primary" : "");
    }

    private List<Screen> previewScreens() {
        GameWindows.Monitor shown = monitors.get(chosenMonitor());
        if (shown == null) {
            shown = monitors.values().stream().filter(GameWindows.Monitor::primary).findFirst()
                    .orElse(null);
        }
        if (shown == null) {
            return List.of(new Screen("", 0, 0, FALLBACK_WIDTH, FALLBACK_HEIGHT));
        }
        String shownDevice = shown.device();
        List<Screen> screens = new ArrayList<>();
        screens.add(shown.screen());
        monitors.values().stream()
                .filter(found -> !found.device().equals(shownDevice))
                .map(GameWindows.Monitor::screen)
                .forEach(screens::add);
        return screens;
    }

    private void rebuildTiles() {
        layoutTiles.getChildren().clear();
        for (Layout layout : Layout.values()) {
            if (layout.screens() <= monitors.size()) {
                layoutTiles.getChildren().add(tile(layout, null));
            }
        }
        customTiles.getChildren().clear();
        for (CustomLayout layout : customLayouts) {
            Button delete = Pages.button("✕", "icon-button");
            delete.setOnAction(event -> delete(layout));
            customTiles.getChildren().add(tile(layout, delete));
        }
        customTiles.setVisible(!customLayouts.isEmpty());
        noCustom.setVisible(customLayouts.isEmpty());
    }

    private VBox tile(Arrangement arrangement, Button extra) {
        Label title = new Label(arrangement.label());
        title.getStyleClass().add("layout-title");
        title.setMaxWidth(TILE_WIDTH - 28);
        boolean current = arrangement.key().equals(settings.lastLayout());
        StackPane heading = new StackPane(title);
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.setMaxWidth(Double.MAX_VALUE);
        heading.setMinHeight(HEADING_HEIGHT);
        heading.setPrefHeight(HEADING_HEIGHT);
        if (arrangement instanceof CustomLayout custom) {
            title.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    rename(custom, heading, title);
                }
            });
        }
        Button arrange = Pages.button("Arrange", "primary-button");
        arrange.setMaxWidth(Double.MAX_VALUE);
        arrange.setOnAction(event -> {
            remember(arrangement);
            if (state.fleet() != null) {
                state.fleet().arrange(arrangement, chosenMonitor());
            } else {
                state.status().set(arrangement.label() + " will be used at launch");
            }
        });
        HBox.setHgrow(arrange, Priority.ALWAYS);
        HBox actions = extra == null ? new HBox(arrange) : new HBox(8, arrange, extra);
        actions.setAlignment(Pos.CENTER);
        VBox tile = new VBox(10, heading, preview(arrangement), actions);
        tile.setAlignment(Pos.CENTER);
        tile.getStyleClass().add("layout-tile");
        if (current) {
            tile.getStyleClass().add("layout-tile-current");
        }
        tile.setPrefWidth(TILE_WIDTH);
        tile.setMinWidth(TILE_WIDTH);
        tile.setMaxWidth(TILE_WIDTH);
        return tile;
    }

    private Pane preview(Arrangement arrangement) {
        List<Screen> screens = previewScreens();
        List<Placement> placements = arrangement.placements(screens);
        Set<Screen> used = new LinkedHashSet<>();
        used.add(screens.getFirst());
        List<int[]> windows = new ArrayList<>();
        for (int slot = 0; slot < placements.size(); slot++) {
            Placement place = placements.get(slot);
            Screen home = screenAt(screens, place.x() + place.width() / 2.0,
                    place.y() + place.height() / 2.0);
            used.add(home);
            windows.add(arrangement.maximized(slot) ? home.area() : place.content(0, 0, 0, 0));
        }
        int left = used.stream().mapToInt(Screen::x).min().orElse(0);
        int top = used.stream().mapToInt(Screen::y).min().orElse(0);
        int right = used.stream().mapToInt(screen -> screen.x() + screen.width()).max().orElse(1);
        int bottom = used.stream().mapToInt(screen -> screen.y() + screen.height()).max()
                .orElse(1);
        Screen first = screens.getFirst();
        double tallest = PREVIEW_WIDTH * first.height() / first.width();
        double scale = Math.min(PREVIEW_WIDTH / (right - left), tallest / (bottom - top));

        Pane desktop = new Pane();
        double width = (right - left) * scale;
        double height = (bottom - top) * scale;
        desktop.setMinSize(width, height);
        desktop.setPrefSize(width, height);
        desktop.setMaxSize(width, height);
        desktop.setClip(new Rectangle(width, height));
        for (Screen screen : used) {
            Region frame = new Region();
            frame.getStyleClass().add("layout-screen");
            frame.relocate((screen.x() - left) * scale, (screen.y() - top) * scale);
            frame.setPrefSize(screen.width() * scale, screen.height() * scale);
            frame.resize(screen.width() * scale, screen.height() * scale);
            desktop.getChildren().add(frame);
        }
        for (int slot = placements.size() - 1; slot >= 0; slot--) {
            Placement place = placements.get(slot);
            int[] content = windows.get(slot);
            Label number = new Label(Integer.toString(slot + 1));
            number.getStyleClass().add("layout-number");
            StackPane window = new StackPane(number);
            window.setAlignment(place.anchorBottom()
                    ? (place.anchorRight() ? Pos.BOTTOM_RIGHT : Pos.BOTTOM_LEFT)
                    : (place.anchorRight() ? Pos.TOP_RIGHT : Pos.TOP_LEFT));
            window.getStyleClass().addAll(slot == 0 ? "layout-window-main" : "layout-window",
                    "layout-slot-" + (slot == 0 ? 1 : (slot - 1) % (SLOT_COLOURS - 1) + 2));
            if (arrangement.stacked() && !arrangement.maximized(slot)) {
                window.getStyleClass().add("layout-window-solid");
            }
            window.relocate((content[0] - left) * scale, (content[1] - top) * scale);
            window.setPrefSize(content[2] * scale, content[3] * scale);
            window.resize(content[2] * scale, content[3] * scale);
            desktop.getChildren().add(window);
        }
        StackPane frame = new StackPane(desktop);
        frame.setAlignment(Pos.CENTER);
        frame.setMinHeight(tallest);
        frame.setPrefHeight(tallest);
        return frame;
    }

    private static Screen screenAt(List<Screen> screens, double x, double y) {
        return screens.stream()
                .filter(screen -> screen.contains(x, y))
                .findFirst()
                .orElse(screens.getFirst());
    }

    private void saveCurrentWindows() {
        if (state.fleet() == null) {
            state.alert(state.installProblem());
            return;
        }
        Dialogs.askText("Name this layout",
                "It keeps where each game window is now, the main client first and the others "
                        + "in launch order.",
                nextName(), "Save")
                .ifPresent(name -> state.fleet().capture(name,
                        layout -> Platform.runLater(() -> add(layout))));
    }

    private String nextName() {
        int number = customLayouts.size() + 1;
        while (true) {
            String candidate = "Custom " + number;
            if (customLayouts.stream().noneMatch(layout -> layout.name().equals(candidate))) {
                return candidate;
            }
            number++;
        }
    }

    private void rename(CustomLayout layout, StackPane heading, Label title) {
        TextField field = new TextField(layout.name());
        field.getStyleClass().add("layout-title-field");
        field.setMaxWidth(TILE_WIDTH - 28);
        boolean[] finished = {false};
        List<Node> shown = List.copyOf(heading.getChildren());
        Runnable restore = () -> heading.getChildren().setAll(shown);
        Runnable commit = () -> {
            if (finished[0]) {
                return;
            }
            finished[0] = true;
            String name = field.getText().trim();
            boolean taken = customLayouts.stream().anyMatch(other ->
                    !other.id().equals(layout.id()) && other.name().equals(name));
            if (name.isEmpty() || name.equals(layout.name())) {
                restore.run();
            } else if (taken) {
                restore.run();
                Platform.runLater(() ->
                        state.alert("There is already a layout called " + name + "."));
            } else {
                customLayouts.replaceAll(existing ->
                        existing.id().equals(layout.id()) ? existing.named(name) : existing);
                storeCustomLayouts();
            }
        };
        field.setOnAction(event -> commit.run());
        field.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                finished[0] = true;
                restore.run();
            }
        });
        field.focusedProperty().addListener((source, was, now) -> {
            if (!now) {
                commit.run();
            }
        });
        heading.getChildren().setAll(field);
        field.requestFocus();
        field.selectAll();
    }

    private void remember(Arrangement arrangement) {
        if (arrangement.key().equals(settings.lastLayout())) {
            return;
        }
        settings.lastLayout(arrangement.key());
        try {
            settings.save();
        } catch (RuntimeException failure) {
            state.alert("Could not save settings: " + failure.getMessage());
        }
        rebuildTiles();
    }

    private void add(CustomLayout layout) {
        boolean replacesCurrent = customLayouts.stream().anyMatch(existing ->
                existing.name().equals(layout.name())
                        && existing.key().equals(settings.lastLayout()));
        if (replacesCurrent) {
            settings.lastLayout(layout.key());
        }
        customLayouts.removeIf(existing -> existing.name().equals(layout.name()));
        customLayouts.add(layout);
        storeCustomLayouts();
    }

    private void delete(CustomLayout layout) {
        if (Dialogs.confirm("Delete " + layout.name() + "?",
                "This can't be undone.", "Delete", true)) {
            customLayouts.removeIf(existing -> existing.id().equals(layout.id()));
            if (layout.key().equals(settings.lastLayout())) {
                settings.lastLayout("");
            }
            storeCustomLayouts();
        }
    }

    private void storeCustomLayouts() {
        settings.customLayouts(customLayouts);
        try {
            settings.save();
        } catch (RuntimeException failure) {
            state.alert("Could not save layouts: " + failure.getMessage());
        }
        rebuildTiles();
    }
}

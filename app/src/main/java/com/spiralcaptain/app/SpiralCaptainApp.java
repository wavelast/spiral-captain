package com.spiralcaptain.app;

import com.spiralcaptain.app.control.Fleet;
import com.spiralcaptain.app.control.RunningClient;
import com.spiralcaptain.app.launch.GameInstall;
import com.spiralcaptain.app.launch.GameUpdates;
import com.spiralcaptain.app.model.Account;
import com.spiralcaptain.app.ui.AccountRow;
import com.spiralcaptain.app.ui.AccountsPage;
import com.spiralcaptain.app.ui.AppState;
import com.spiralcaptain.app.ui.Dialogs;
import com.spiralcaptain.app.ui.ArrangePage;
import com.spiralcaptain.app.ui.Logo;
import com.spiralcaptain.app.ui.Pages;
import com.spiralcaptain.app.ui.SettingsPage;
import com.spiralcaptain.app.ui.Theme;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.List;

public final class SpiralCaptainApp extends Application {

    private AppState state;
    private final StackPane pages = new StackPane();

    private ToggleButton arrangeTab;
    private HBox launchControls;
    private final Label selectionSummary = new Label();
    private final Label runningSummary = new Label();
    private final Button closeAll = new Button("Close all clients");
    private final Button closeFollowers = new Button("Close follower clients");
    private final HBox closeRow = new HBox(2, closeAll, closeFollowers);
    private final VBox toast = new VBox(10);
    private final Label toastTitle = new Label();
    private final Label toastDetail = new Label();
    private final Button toastUpdate = new Button("Update");
    private final Button toastLater = new Button("Later");

    @Override
    public void start(Stage stage) {
        state = new AppState();
        state.alerts(this::showAlert);

        AccountsPage accounts = new AccountsPage(state);
        SettingsPage settings = new SettingsPage(state);
        ArrangePage arrange = new ArrangePage(state);

        BorderPane main = new BorderPane();
        main.setCenter(pages);
        main.getStyleClass().add("main-area");

        BorderPane root = new BorderPane();
        root.setLeft(sidebar(accounts, Pages.scrolling(arrange), settings));
        root.setCenter(main);
        root.getStyleClass().add("app-root");

        StackPane layers = new StackPane(root);
        Dialogs.install(layers);
        buildToast();
        Scene scene = new Scene(layers);
        Theme.apply(scene, Theme.darkFor(state.settings().theme()));
        Platform.getPreferences().colorSchemeProperty().addListener((source, was, now) ->
                Theme.setDark(scene, Theme.darkFor(state.settings().theme())));
        stage.setTitle("Spiral Captain");
        stage.setMinWidth(1010);
        stage.setMinHeight(560);
        stage.setWidth(stage.getMinWidth());
        stage.setHeight(stage.getMinHeight());
        stage.getIcons().setAll(Logo.windowIcons());
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> state.shutdown());

        state.onRefresh(this::refreshSidebar);
        state.rows().forEach(this::watchTick);
        state.rows().addListener((javafx.collections.ListChangeListener<AccountRow>) change -> {
            while (change.next()) {
                change.getAddedSubList().forEach(this::watchTick);
            }
            refreshSidebar();
        });
        stage.show();
        state.refresh();
        if (!state.installProblem().isEmpty()) {
            showAlert(state.installProblem());
        }
        checkForUpdates();
    }

    private VBox sidebar(Node accounts, Node arrange, Node settings) {
        VBox brand = new VBox(Logo.create());
        brand.getStyleClass().add("brand");

        ToggleGroup group = new ToggleGroup();
        ToggleButton accountsTab = navButton("Accounts", Theme.Icon.ACCOUNTS, accounts, group);
        arrangeTab = navButton("Arrange", Theme.Icon.ARRANGE, arrange, group);
        ToggleButton settingsTab = navButton("Settings", Theme.Icon.SETTINGS, settings, group);
        group.selectedToggleProperty().addListener((source, was, now) -> {
            if (now == null && was != null) {
                group.selectToggle(was);
            }
        });
        accountsTab.setSelected(true);
        VBox nav = new VBox(4, accountsTab, arrangeTab, settingsTab);
        nav.getStyleClass().add("nav");

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);

        Button launch = new Button("Launch", Theme.icon(Theme.Icon.LAUNCH));
        launch.getStyleClass().addAll("launch-button", "launch-main");
        launch.setMaxWidth(Double.MAX_VALUE);
        launch.setContentDisplay(ContentDisplay.LEFT);
        launch.setOnAction(event -> launchTicked());
        Pages.describe(launch, "Launch the ticked accounts");

        MenuItem all = new MenuItem("Launch all");
        all.setOnAction(event -> launchAll());
        MenuItem alts = new MenuItem("Launch alts only");
        alts.setOnAction(event -> launchAlts());
        ContextMenu choices = new ContextMenu(all, alts);
        Button more = new Button(null, Theme.icon(Theme.Icon.DROPDOWN));
        more.getStyleClass().addAll("launch-button", "launch-more");
        more.setMaxHeight(Double.MAX_VALUE);
        more.setOnAction(event -> choices.show(more, Side.TOP, 0, -4));
        Pages.describe(more, "Launch every account, or every account but the main one");

        HBox.setHgrow(launch, Priority.ALWAYS);
        launchControls = new HBox(launch, more);
        launchControls.setMaxWidth(Double.MAX_VALUE);

        selectionSummary.getStyleClass().add("sidebar-caption");
        runningSummary.getStyleClass().add("sidebar-caption");
        closeFollowers.getStyleClass().add("link-button");
        closeFollowers.setOnAction(event -> {
            if (state.fleet() != null) {
                state.fleet().quitFollowers();
            }
        });
        closeFollowers.managedProperty().bind(closeFollowers.visibleProperty());
        closeAll.getStyleClass().add("link-button");
        closeAll.setOnAction(event -> {
            if (state.fleet() != null) {
                state.fleet().quitAll();
            }
        });
        closeAll.managedProperty().bind(closeAll.visibleProperty());
        closeAll.setMinWidth(Region.USE_PREF_SIZE);
        closeFollowers.setMinWidth(Region.USE_PREF_SIZE);
        closeRow.setAlignment(Pos.CENTER);
        closeRow.managedProperty().bind(closeRow.visibleProperty());

        VBox launchArea = new VBox(8, selectionSummary, launchControls, runningSummary,
                closeRow);
        launchArea.setAlignment(Pos.CENTER);
        launchArea.getStyleClass().add("launch-area");

        VBox sidebar = new VBox(brand, nav, spacer, statusBar(), launchArea);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(250);
        sidebar.setMinWidth(250);
        return sidebar;
    }

    private ToggleButton navButton(String text, Theme.Icon icon, Node page, ToggleGroup group) {
        ToggleButton button = new ToggleButton(text, Theme.icon(icon));
        button.setToggleGroup(group);
        button.getStyleClass().add("nav-button");
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setGraphicTextGap(14);
        button.selectedProperty().addListener((source, was, now) -> {
            if (now) {
                pages.getChildren().setAll(page);
            }
        });
        return button;
    }

    private void watchTick(AccountRow row) {
        row.selectedProperty().addListener((source, was, now) -> refreshSidebar());
    }

    private void refreshSidebar() {
        List<Account> checked = state.checkedAccounts();
        int total = state.rows().size();
        selectionSummary.setText(total == 0
                ? "Add an account to begin"
                : checked.size() + " of " + total + " accounts ticked");

        Fleet fleet = state.fleet();
        long running = fleet == null ? 0
                : fleet.clients().stream().filter(RunningClient::alive).count();
        runningSummary.setText(running == 0 ? "No clients running"
                : running + (running == 1 ? " client running" : " clients running"));
        closeFollowers.setVisible(fleet != null
                && fleet.followers().stream().anyMatch(RunningClient::alive));
        closeAll.setVisible(running > 0);
        closeRow.setVisible(running > 0);
    }

    private HBox statusBar() {
        Region dot = new Region();
        dot.getStyleClass().add("status-dot");
        HBox.setMargin(dot, new Insets(5, 0, 0, 0));
        Label text = new Label();
        text.textProperty().bind(state.status());
        text.getStyleClass().add("status-text");
        text.setWrapText(true);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        HBox bar = new HBox(10, dot, text);
        bar.setAlignment(Pos.TOP_LEFT);
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private void launchTicked() {
        launch(state.checkedAccounts(), "Tick the Launch box on the accounts you want to start.");
    }

    private void launchAll() {
        launch(state.accounts(), "Add an account first.");
    }

    private void launchAlts() {
        launch(state.accounts().stream().filter(account -> !account.main()).toList(),
                "There are no alts to launch: every account except #1 is an alt.");
    }

    private void launch(List<Account> toLaunch, String nothing) {
        if (!state.installProblem().isEmpty()) {
            showAlert(state.installProblem());
            return;
        }
        if (toLaunch.isEmpty()) {
            showAlert(nothing);
            return;
        }
        startClients(toLaunch);
    }

    private void checkForUpdates() {
        if (!state.settings().checkForUpdates() || state.install() == null) {
            return;
        }
        GameInstall install = GameInstall.at(state.install().directory());
        Thread.ofVirtual().start(() -> {
            GameUpdates.Check check = GameUpdates.check(install);
            Platform.runLater(() -> showUpdate(check));
        });
    }

    private VBox buildToast() {
        toastTitle.getStyleClass().add("toast-title");
        toastDetail.getStyleClass().add("toast-detail");
        toastDetail.setWrapText(true);
        toastDetail.setMinHeight(Region.USE_PREF_SIZE);
        toastUpdate.getStyleClass().add("primary-button");
        toastUpdate.setOnAction(event -> runUpdate());
        toastLater.setOnAction(event -> hideToast());
        HBox buttons = new HBox(8, toastUpdate, toastLater);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        toast.getChildren().setAll(toastTitle, toastDetail, buttons);
        toast.getStyleClass().add("toast");
        toast.setMaxSize(Dialogs.TOAST_WIDTH, Region.USE_PREF_SIZE);
        return toast;
    }

    private void showUpdate(GameUpdates.Check check) {
        if (check.state() != GameUpdates.State.UPDATE_AVAILABLE) {
            return;
        }
        offerUpdate(check);
        Dialogs.showCard(toast);
    }

    private void offerUpdate(GameUpdates.Check check) {
        toastTitle.setText("Game update available");
        toastDetail.setText("Spiral Knights version " + check.latest() + " is out; this install has "
                + check.installed() + ". Clients from an older version may not be able to log in.");
        toastUpdate.setDisable(false);
        toastLater.setDisable(false);
    }

    private void hideToast() {
        Dialogs.dismiss(toast);
    }

    private void runUpdate() {
        if (state.fleet() != null && state.fleet().anyRunning()) {
            toastTitle.setText("Close the running clients first");
            toastDetail.setText("The update replaces the game's files, which the running clients "
                    + "are using. Close them, then press Update again.");
            return;
        }
        GameInstall install = GameInstall.at(state.install().directory());
        launchControls.setDisable(true);
        toastUpdate.setDisable(true);
        toastLater.setDisable(true);
        toastTitle.setText("Updating Spiral Knights");
        toastDetail.setText("The game's own updater is installing the update. This can take a few "
                + "minutes; Launch is paused until it finishes.");
        state.fleet().report("Updating Spiral Knights; this can take a few minutes");
        Thread.ofVirtual().start(() -> {
            String problem = "";
            try {
                GameUpdates.update(install);
            } catch (IOException failure) {
                problem = failure.getMessage();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                problem = "the update was cancelled";
            }
            GameUpdates.Check after = GameUpdates.check(GameInstall.at(install.directory()));
            String failure = problem;
            Platform.runLater(() -> {
                launchControls.setDisable(false);
                if (after.state() == GameUpdates.State.UP_TO_DATE) {
                    hideToast();
                    state.fleet().report("Spiral Knights is updated to version "
                            + after.installed());
                } else {
                    toastUpdate.setDisable(false);
                    toastLater.setDisable(false);
                    toastTitle.setText("The update did not finish");
                    toastDetail.setText((failure.isEmpty() ? "" : "The updater says " + failure
                            + ". ") + "Press Update to try again, or start the game once through "
                            + "Steam so it can update.");
                    state.fleet().report("The game update did not finish");
                }
            });
        });
    }

    private void startClients(List<Account> toLaunch) {
        state.fleet().launch(toLaunch);
        arrangeTab.setSelected(true);
    }

    private void showAlert(String message) {
        Dialogs.inform(message);
    }

    public static void main(String[] args) {
        launch(args);
    }
}

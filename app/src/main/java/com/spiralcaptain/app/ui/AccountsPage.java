package com.spiralcaptain.app.ui;

import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Label;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.CheckBoxTableCell;
import javafx.scene.control.TextField;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.Dragboard;
import javafx.scene.input.KeyCode;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.function.Function;
import java.util.Optional;

public final class AccountsPage extends VBox {

    private final AppState state;
    private static final DataFormat ROW_INDEX =
            new DataFormat("application/x-spiral-captain-account-row");
    private static final PseudoClass DROP_TARGET = PseudoClass.getPseudoClass("drop-target");

    private final TableView<AccountRow> table;
    private TableColumn<AccountRow, String> accountColumn;

    public AccountsPage(AppState state) {
        this.state = state;
        getStyleClass().add("page");
        setSpacing(14);

        table = buildTable();
        FittedTable fitted = new FittedTable(table);
        VBox.setVgrow(fitted, Priority.ALWAYS);

        getChildren().addAll(
                Pages.header("Accounts",
                        "Account #1 is the main client and launches first. Drag an account "
                                + "up or down to reorder; drag it to #1 to make it the main "
                                + "client. The account Steam is logged into is always #1. Type a knight's name to "
                                + "log straight in as that knight, or leave it empty to pick "
                                + "in the game."),
                buildToolbar(),
                fitted);
        state.onRefresh(table::refresh);
    }

    private HBox buildToolbar() {
        Button add = Pages.button("Add account", "primary-button");
        add.setOnAction(event -> {
            AccountRow row = state.addAccount();
            table.getSelectionModel().select(row);
            int index = state.rows().indexOf(row);
            table.scrollTo(index);
            Platform.runLater(() -> {
                table.layout();
                table.requestFocus();
                table.edit(index, accountColumn);
            });
        });

        Button password = Pages.button("Set password", null);
        password.setOnAction(event -> selected().ifPresent(this::setPassword));

        Button steam = Pages.button("Add Steam account", null);
        steam.setOnAction(event -> state.addSteamAccount().ifPresent(row ->
                table.getSelectionModel().select(row)));
        Runnable greyOutSteam = () -> steam.setDisable(state.steamRow().isPresent());
        state.onRefresh(greyOutSteam);
        state.rows().addListener((javafx.collections.ListChangeListener<AccountRow>) change ->
                greyOutSteam.run());
        greyOutSteam.run();

        Button remove = Pages.button("Remove", "danger-button");
        remove.setOnAction(event -> selected().ifPresent(this::remove));

        var noSelection = table.getSelectionModel().selectedItemProperty().isNull();
        for (Button button : List.of(password, remove)) {
            button.disableProperty().bind(noSelection);
        }
        password.disableProperty().unbind();
        Runnable passwordState = () -> password.setDisable(
                selected().map(row -> row.steamProperty().get()).orElse(true));
        table.getSelectionModel().selectedItemProperty()
                .addListener((source, was, now) -> passwordState.run());
        state.onRefresh(passwordState);
        passwordState.run();

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox bar = new HBox(8, add, steam, password, spacer, remove);
        bar.getStyleClass().add("toolbar");
        return bar;
    }

    private TableView<AccountRow> buildTable() {
        TableView<AccountRow> view = new TableView<>(state.rows());
        view.setEditable(true);
        view.getSelectionModel().setSelectionMode(SelectionMode.SINGLE);
        view.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        view.setPlaceholder(new Label("No accounts yet. Press Add account to create one."));
        view.setRowFactory(table -> draggable(view, new TableRow<>() {
            @Override
            protected void updateItem(AccountRow row, boolean empty) {
                super.updateItem(row, empty);
                pseudoClassStateChanged(Pages.MAIN, !empty && getIndex() == 0);
            }
        }));

        TableColumn<AccountRow, AccountRow> number = new TableColumn<>("#");
        number.setCellValueFactory(data -> new ReadOnlyObjectWrapper<>(data.getValue()));
        number.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(AccountRow row, boolean empty) {
                super.updateItem(row, empty);
                if (empty || row == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                setText(Integer.toString(getIndex() + 1));
                setGraphic(row.steamProperty().get() ? badge("Steam", "steam-badge")
                        : getIndex() == 0 ? badge("Main", "main-badge")
                        : null);
                setContentDisplay(javafx.scene.control.ContentDisplay.RIGHT);
                setGraphicTextGap(8);
            }
        });
        number.getStyleClass().add("number-column");
        number.setMinWidth(90);
        number.setMaxWidth(90);

        TableColumn<AccountRow, Boolean> launch = new TableColumn<>("Launch");
        launch.setCellValueFactory(data -> data.getValue().selectedProperty());
        launch.setCellFactory(CheckBoxTableCell.forTableColumn(launch));
        launch.setEditable(true);
        launch.setMinWidth(64);
        launch.setMaxWidth(80);

        TableColumn<AccountRow, String> accountName = new TableColumn<>("Account");
        accountName.setCellValueFactory(data -> data.getValue().accountNameProperty());
        accountName.setCellFactory(column ->
                new NameCell(AccountRow::accountNameProperty, null));
        accountName.setOnEditCommit(event ->
                event.getRowValue().accountNameProperty().set(event.getNewValue()));
        accountName.setPrefWidth(150);
        accountName.setMinWidth(106);

        TableColumn<AccountRow, String> knight = new TableColumn<>("Knight");
        knight.setCellValueFactory(data -> data.getValue().knightNameProperty());
        knight.setCellFactory(column ->
                new NameCell(AccountRow::knightNameProperty, "Enter knight name"));
        knight.setOnEditCommit(event ->
                event.getRowValue().knightNameProperty().set(event.getNewValue()));
        knight.setPrefWidth(170);
        knight.setMinWidth(120);

        TableColumn<AccountRow, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(data -> data.getValue().statusProperty());
        status.setEditable(false);
        status.setCellFactory(column -> new StatusCell());
        status.setMinWidth(200);

        view.getColumns().setAll(List.of(number, launch, accountName, knight, status));
        accountColumn = accountName;
        view.setContextMenu(launchMenu());

        view.getColumns().forEach(column -> {
            column.setSortable(false);
            column.setReorderable(false);
        });
        return view;
    }

    private static final int ROWS_SHOWN = 4;

    private static final double MIN_ROW_HEIGHT = 40;

    private static final class FittedTable extends Region {

        private final TableView<?> table;

        FittedTable(TableView<?> table) {
            this.table = table;
            getChildren().add(table);
        }

        @Override
        protected double computeMinHeight(double width) {
            return 0;
        }

        @Override
        protected double computePrefHeight(double width) {
            return 0;
        }

        @Override
        protected double computeMinWidth(double height) {
            return table.minWidth(height);
        }

        @Override
        protected double computePrefWidth(double height) {
            return table.prefWidth(height);
        }

        @Override
        protected void layoutChildren() {
            double width = getWidth();
            double height = getHeight();
            if (!(table.lookup(".column-header-background") instanceof Region header)) {
                table.resizeRelocate(0, 0, width, height);
                Platform.runLater(this::requestLayout);
                return;
            }
            double headerHeight = header.getHeight() > 0 ? header.getHeight()
                    : header.prefHeight(width);
            double chrome = headerHeight + table.snappedTopInset() + table.snappedBottomInset();
            double row = Math.max(MIN_ROW_HEIGHT, Math.floor((height - chrome) / ROWS_SHOWN));
            if (row != table.getFixedCellSize()) {
                table.setFixedCellSize(row);
            }
            table.resizeRelocate(0, 0, width, Math.min(height, chrome + ROWS_SHOWN * row));
        }
    }

    private ContextMenu launchMenu() {
        MenuItem all = new MenuItem("Select all for launch");
        all.setOnAction(event -> state.rows().forEach(row -> row.selectedProperty().set(true)));
        MenuItem none = new MenuItem("Deselect all from launch");
        none.setOnAction(event -> state.rows().forEach(row -> row.selectedProperty().set(false)));
        return new ContextMenu(all, none);
    }

    private static final class NameCell extends TableCell<AccountRow, String> {

        private static final PseudoClass HINT = PseudoClass.getPseudoClass("hint");

        private final Function<AccountRow, StringProperty> property;
        private final String prompt;
        private TextField field;
        private boolean discard;

        NameCell(Function<AccountRow, StringProperty> property, String prompt) {
            this.property = property;
            this.prompt = prompt;
            setOnMouseClicked(event -> {
                if (!isEmpty() && !isEditing() && getTableView() != null) {
                    getTableView().edit(getIndex(), getTableColumn());
                }
            });
        }

        @Override
        public void startEdit() {
            super.startEdit();
            if (!isEditing()) {
                return;
            }
            discard = false;
            field = new TextField(getItem());
            field.setPromptText(prompt);
            field.setOnAction(event -> commitEdit(field.getText()));
            field.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ESCAPE) {
                    discard = true;
                    cancelEdit();
                }
            });
            field.focusedProperty().addListener((source, was, now) -> {
                if (!now && isEditing()) {
                    commitEdit(field.getText());
                }
            });
            setText(null);
            setGraphic(field);
            pseudoClassStateChanged(HINT, false);
            field.selectAll();
            field.requestFocus();
        }

        @Override
        public void cancelEdit() {
            String typed = field == null ? null : field.getText();
            super.cancelEdit();
            AccountRow row = getTableRow() == null ? null : getTableRow().getItem();
            if (!discard && typed != null && row != null) {
                property.apply(row).set(typed.trim());
            }
            field = null;
            showValue(row == null ? getItem() : property.apply(row).get());
        }

        @Override
        public void commitEdit(String value) {
            String trimmed = value == null ? null : value.trim();
            super.commitEdit(trimmed);
            field = null;
            showValue(trimmed);
        }

        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty) {
                setText(null);
                setGraphic(null);
            } else if (isEditing() && field != null) {
                setText(null);
                setGraphic(field);
            } else {
                showValue(item);
            }
        }

        private void showValue(String value) {
            boolean blank = value == null || value.isBlank();
            boolean hint = blank && prompt != null;
            setGraphic(null);
            setText(hint ? prompt : value);
            pseudoClassStateChanged(HINT, hint);
        }
    }

    private static Label badge(String text, String styleClass) {
        Label badge = new Label(text);
        badge.getStyleClass().add(styleClass);
        return badge;
    }

    private static final class StatusCell extends TableCell<AccountRow, String> {

        StatusCell() {
            setWrapText(true);
        }

        @Override
        protected void updateItem(String status, boolean empty) {
            super.updateItem(status, empty);
            setText(empty ? null : status);
            if (empty || status == null) {
                setTooltip(null);
            } else {
                Pages.describe(this, status);
            }
        }
    }

    private TableRow<AccountRow> draggable(TableView<AccountRow> view, TableRow<AccountRow> row) {
        row.setOnDragDetected(event -> {
            if (row.isEmpty() || view.getEditingCell() != null) {
                return;
            }
            Dragboard board = row.startDragAndDrop(TransferMode.MOVE);
            board.setDragView(row.snapshot(null, null));
            ClipboardContent content = new ClipboardContent();
            content.put(ROW_INDEX, row.getIndex());
            board.setContent(content);
            event.consume();
        });
        row.setOnDragOver(event -> {
            if (event.getDragboard().hasContent(ROW_INDEX)) {
                event.acceptTransferModes(TransferMode.MOVE);
                event.consume();
            }
        });
        row.setOnDragEntered(event -> {
            if (event.getDragboard().hasContent(ROW_INDEX)) {
                row.pseudoClassStateChanged(DROP_TARGET, true);
            }
        });
        row.setOnDragExited(event -> row.pseudoClassStateChanged(DROP_TARGET, false));
        row.setOnDragDropped(event -> {
            Dragboard board = event.getDragboard();
            if (!board.hasContent(ROW_INDEX)) {
                return;
            }
            int from = (Integer) board.getContent(ROW_INDEX);
            if (from >= 0 && from < state.rows().size()) {
                AccountRow dragged = state.rows().get(from);
                int to = row.isEmpty() ? state.rows().size() - 1 : row.getIndex();
                if (to != from && !state.moveTo(dragged, to)) {
                    explainSteamPin();
                }
                view.getSelectionModel().select(dragged);
            }
            event.setDropCompleted(true);
            event.consume();
        });
        return row;
    }

    private void explainSteamPin() {
        state.alert("The Steam account is always the main client, so it stays at #1. "
                + "Untick Steam on it to choose a different main client.");
    }

    private void setPassword(AccountRow row) {
        PasswordDialog.askFor(row.account()).ifPresent(digest -> {
            row.account().passwordDigest(digest);
            state.save();
            state.refresh();
        });
    }

    private void remove(AccountRow row) {
        if (state.fleet() != null && state.fleet().isRunning(row.account())) {
            state.alert("That account has a client running. Close it first.");
            return;
        }
        if (Dialogs.confirm("Remove " + row.account().displayName() + " from the launcher?",
                "Its saved password goes with it. The game account itself is not touched.",
                "Remove", true)) {
            state.remove(row);
        }
    }

    private Optional<AccountRow> selected() {
        return Optional.ofNullable(table.getSelectionModel().getSelectedItem());
    }
}

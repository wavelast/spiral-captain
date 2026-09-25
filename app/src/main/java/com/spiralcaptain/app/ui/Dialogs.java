package com.spiralcaptain.app.ui;

import javafx.animation.FadeTransition;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.value.ObservableBooleanValue;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.function.Supplier;

public final class Dialogs {

    public static final double TOAST_WIDTH = 440;
    private static final Duration FADE = Duration.millis(180);

    private static StackPane host;
    private static final VBox messages = new VBox(12);
    private static final Deque<Trap> traps = new ArrayDeque<>();

    private record Trap(VBox card, Node home) {
    }

    private Dialogs() {
    }

    public static void install(StackPane layers) {
        host = layers;
        messages.setAlignment(Pos.CENTER);
        messages.setPickOnBounds(false);
        layers.getChildren().add(messages);
        layers.sceneProperty().addListener((source, was, scene) -> {
            if (scene != null) {
                scene.focusOwnerProperty().addListener((owner, before, now) -> keepFocus(now));
            }
        });
    }

    public static void inform(String message) {
        inform(message, null);
    }

    public static void inform(String message, String detail) {
        if (host == null) {
            return;
        }
        Button ok = button("OK", "primary-button");
        VBox card = card(message, detail, null, ok);
        ok.setOnAction(event -> dismiss(card));
        showCard(card);
    }

    public static void showCard(VBox card) {
        if (host == null || messages.getChildren().contains(card)) {
            return;
        }
        card.setOpacity(0);
        messages.getChildren().add(card);
        fade(card, 1, null);
    }

    public static void dismiss(VBox card) {
        if (messages.getChildren().contains(card)) {
            fade(card, 0, () -> messages.getChildren().remove(card));
        }
    }

    public static boolean confirm(String message, String detail, String action,
            boolean destructive) {
        return ask(message, detail, null, action,
                destructive ? "danger-button" : "primary-button", null, () -> Boolean.TRUE, null)
                .orElse(false);
    }

    public static Optional<String> askText(String message, String detail, String initial,
            String action) {
        TextField field = new TextField(initial);
        field.setMaxWidth(Double.MAX_VALUE);
        Platform.runLater(field::selectAll);
        return ask(message, detail, field, action, "primary-button",
                Bindings.createBooleanBinding(() -> !field.getText().isBlank(),
                        field.textProperty()),
                () -> field.getText().trim(), field);
    }

    public static <T> Optional<T> ask(String message, String detail, Node content,
            String action, String actionStyle, ObservableBooleanValue ready, Supplier<T> result,
            Node focus) {
        if (host == null) {
            return Optional.empty();
        }
        Object key = new Object();
        Button confirm = button(action, actionStyle);
        Button cancel = button("Cancel", null);
        if (ready != null) {
            confirm.disableProperty().bind(Bindings.not(ready));
        }
        VBox card = card(message, detail, content, confirm, cancel);
        Region scrim = new Region();
        scrim.getStyleClass().add("toast-scrim");
        boolean[] finished = {false};
        Runnable close = () -> {
            traps.removeIf(trap -> trap.card() == card);
            hide(card);
            hide(scrim);
        };
        confirm.setOnAction(event -> {
            if (!finished[0]) {
                finished[0] = true;
                T value = result.get();
                close.run();
                Platform.exitNestedEventLoop(key, Optional.ofNullable(value));
            }
        });
        cancel.setOnAction(event -> {
            if (!finished[0]) {
                finished[0] = true;
                close.run();
                Platform.exitNestedEventLoop(key, Optional.empty());
            }
        });
        card.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                cancel.fire();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                if (cancel.isFocused()) {
                    cancel.fire();
                } else if (!confirm.isDisabled()) {
                    confirm.fire();
                }
                event.consume();
            }
        });
        Node home = focus != null ? focus : confirm;
        traps.push(new Trap(card, home));
        show(scrim);
        show(card);
        Platform.runLater(home::requestFocus);
        @SuppressWarnings("unchecked")
        Optional<T> answer = (Optional<T>) Platform.enterNestedEventLoop(key);
        return answer;
    }

    public static VBox card(String message, String detail, Node content, Button... buttons) {
        Label title = text(message, "toast-title");
        VBox card = new VBox(10, title);
        if (detail != null && !detail.isEmpty()) {
            card.getChildren().add(text(detail, "toast-detail"));
        }
        if (content != null) {
            card.getChildren().add(content);
        }
        HBox row = new HBox(8, buttons);
        row.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().add(row);
        card.getStyleClass().add("toast");
        card.setMaxSize(TOAST_WIDTH, Region.USE_PREF_SIZE);
        StackPane.setAlignment(card, Pos.CENTER);
        return card;
    }

    private static Label text(String value, String styleClass) {
        Label label = new Label(value);
        label.getStyleClass().add(styleClass);
        label.setWrapText(true);
        label.setMinHeight(Region.USE_PREF_SIZE);
        return label;
    }

    private static Button button(String text, String styleClass) {
        Button button = new Button(text);
        if (styleClass != null) {
            button.getStyleClass().add(styleClass);
        }
        button.setMinWidth(Region.USE_PREF_SIZE);
        return button;
    }

    private static void show(Node node) {
        node.setOpacity(0);
        host.getChildren().add(node);
        fade(node, 1, null);
    }

    private static void hide(Node node) {
        fade(node, 0, () -> host.getChildren().remove(node));
    }

    private static void fade(Node node, double to, Runnable after) {
        FadeTransition fade = new FadeTransition(FADE, node);
        fade.setToValue(to);
        if (after != null) {
            fade.setOnFinished(event -> after.run());
        }
        fade.play();
    }

    private static void keepFocus(Node now) {
        Trap trap = traps.peek();
        if (trap == null || now == null || inside(now, trap.card())) {
            return;
        }
        Platform.runLater(trap.home()::requestFocus);
    }

    private static boolean inside(Node node, Parent parent) {
        for (Node step = node; step != null; step = step.getParent()) {
            if (step == parent) {
                return true;
            }
        }
        return false;
    }
}

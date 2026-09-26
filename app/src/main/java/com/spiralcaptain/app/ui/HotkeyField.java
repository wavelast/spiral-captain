package com.spiralcaptain.app.ui;

import com.spiralcaptain.app.model.Hotkey;
import javafx.css.PseudoClass;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

import java.util.Optional;
import java.util.function.Consumer;

final class HotkeyField extends Button {

    private static final PseudoClass LISTENING = PseudoClass.getPseudoClass("listening");
    private static final PseudoClass UNSET = PseudoClass.getPseudoClass("unset");

    private final Consumer<Hotkey> chosen;
    private final Consumer<Boolean> listeningChanged;
    private Optional<Hotkey> current = Optional.empty();
    private boolean listening;

    HotkeyField(Consumer<Hotkey> chosen, Consumer<Boolean> listeningChanged) {
        this.chosen = chosen;
        this.listeningChanged = listeningChanged;
        getStyleClass().add("hotkey-field");
        setOnAction(event -> listen());
        addEventFilter(KeyEvent.KEY_PRESSED, this::pressed);
        addEventFilter(KeyEvent.KEY_RELEASED, event -> {
            if (listening) {
                event.consume();
            }
        });
        addEventFilter(KeyEvent.KEY_TYPED, event -> {
            if (listening) {
                event.consume();
            }
        });
        focusedProperty().addListener((source, was, now) -> {
            if (!now) {
                finish();
            }
        });
        showCurrent();
    }

    void show(Optional<Hotkey> hotkey) {
        current = hotkey;
        if (!listening) {
            showCurrent();
        }
    }

    private void showCurrent() {
        setText(current.map(Hotkey::text).orElse("None"));
        pseudoClassStateChanged(UNSET, current.isEmpty());
    }

    private void listen() {
        if (listening) {
            return;
        }
        listening = true;
        pseudoClassStateChanged(LISTENING, true);
        pseudoClassStateChanged(UNSET, false);
        setText("Press keys");
        listeningChanged.accept(true);
    }

    private void finish() {
        if (!listening) {
            return;
        }
        listening = false;
        pseudoClassStateChanged(LISTENING, false);
        showCurrent();
        listeningChanged.accept(false);
    }

    private void pressed(KeyEvent event) {
        KeyCode code = event.getCode();
        if (!listening) {
            if (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE) {
                event.consume();
                chosen.accept(null);
            }
            return;
        }
        event.consume();
        if (code.isModifierKey() || code == KeyCode.WINDOWS || code == KeyCode.ALT_GRAPH) {
            return;
        }
        if (code == KeyCode.ESCAPE) {
            finish();
            return;
        }
        if (code == KeyCode.BACK_SPACE || code == KeyCode.DELETE) {
            chosen.accept(null);
            finish();
            return;
        }
        Hotkey hotkey = new Hotkey(event.isControlDown(), event.isAltDown(), event.isShiftDown(),
                code.getCode());
        if (!hotkey.usable()) {
            setText(hotkey.ctrl() || hotkey.alt() ? "Pick another key" : "Add Alt or Ctrl");
            return;
        }
        chosen.accept(hotkey);
        finish();
    }
}

package com.spiralcaptain.app.ui;

import com.spiralcaptain.app.model.Account;
import com.spiralcaptain.app.store.Secrets;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.BooleanBinding;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Priority;

import java.util.Optional;

public final class PasswordDialog {

    private PasswordDialog() {
    }

    public static Optional<String> askFor(Account account) {
        PasswordField password = new PasswordField();
        password.setPromptText("Password");
        PasswordField again = new PasswordField();
        again.setPromptText("Repeat");
        Label problem = new Label();
        problem.getStyleClass().add("problem-label");

        GridPane layout = new GridPane();
        layout.setHgap(8);
        layout.setVgap(8);
        layout.addRow(0, new Label("Password"), password);
        layout.addRow(1, new Label("Repeat"), again);
        layout.add(problem, 0, 2, 2, 1);
        GridPane.setHgrow(password, Priority.ALWAYS);
        GridPane.setHgrow(again, Priority.ALWAYS);

        BooleanBinding ready = Bindings.createBooleanBinding(
                () -> !password.getText().isEmpty() && password.getText().equals(again.getText()),
                password.textProperty(), again.textProperty());
        Runnable explain = () -> problem.setText(!again.getText().isEmpty()
                && !password.getText().equals(again.getText())
                ? "The two entries do not match."
                : "");
        password.textProperty().addListener((source, was, now) -> explain.run());
        again.textProperty().addListener((source, was, now) -> explain.run());

        String detail = "Only the digest the game sends is stored, never the password.";
        if (!Secrets.encryptionAvailable()) {
            detail += " Windows encryption is unavailable (" + Secrets.unavailableReason()
                    + "), so the digest will be stored unencrypted.";
        }
        return Dialogs.ask("Set the password for " + account.displayName(), detail, layout,
                "Save", "primary-button", ready, () -> Secrets.digest(password.getText()),
                password);
    }
}

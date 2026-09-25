package com.spiralcaptain.app.ui;

import javafx.css.PseudoClass;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Control;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.util.Duration;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

public final class Pages {

    static final PseudoClass MAIN = PseudoClass.getPseudoClass("main");

    private Pages() {
    }

    static final Duration DESCRIPTION_DELAY = Duration.seconds(1);

    static VBox header(String title, String description) {
        Label heading = new Label(title);
        heading.getStyleClass().add("page-title");
        describe(heading, description);
        VBox box = new VBox(heading);
        box.getStyleClass().add("page-header");
        return box;
    }

    static VBox card(String title, Node... content) {
        return card(title, (String) null, content);
    }

    static VBox card(String title, String description, Node... content) {
        Label heading = new Label(title);
        heading.getStyleClass().add("card-title");
        if (description != null) {
            describe(heading, description);
        }
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.getChildren().add(heading);
        card.getChildren().addAll(content);
        return card;
    }

    static Button button(String text, String styleClass) {
        Button button = new Button(text);
        button.setMinWidth(Region.USE_PREF_SIZE);
        if (styleClass != null) {
            button.getStyleClass().add(styleClass);
        }
        return button;
    }

    public static void describe(Node node, String text) {
        Tooltip tooltip = new Tooltip(text);
        tooltip.setShowDelay(DESCRIPTION_DELAY);
        tooltip.setShowDuration(Duration.INDEFINITE);
        tooltip.setWrapText(true);
        tooltip.setMaxWidth(380);
        tooltip.getStyleClass().add("description");
        if (node instanceof Control control) {
            control.setTooltip(tooltip);
        } else {
            Tooltip.install(node, tooltip);
        }
    }

    static Label hint(String text) {
        Label hint = new Label(text);
        hint.getStyleClass().add("hint");
        hint.setWrapText(true);
        hint.setMinHeight(Region.USE_PREF_SIZE);
        return hint;
    }

    public static ScrollPane scrolling(Region page) {
        ScrollPane scroll = new ScrollPane(page);
        scroll.getStyleClass().add("page-scroll");
        scroll.setFitToWidth(true);
        return scroll;
    }
}

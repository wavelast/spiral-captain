package com.spiralcaptain.app.ui;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Logo {

    private static final int[] ICON_SIZES = {16, 20, 24, 32, 40, 48, 64, 128, 256};

    private static List<Image> windowIcons;

    private static final double CANVAS = 48;

    private static final double DOME_TOP = 1.4;
    private static final double SHOULDER = 11.5;
    private static final double BOTTOM = 20.4;
    private static final double GAP_TOP = 16.2;
    private static final double VISOR_TOP = 8.6;
    private static final double NOTCH_BOTTOM = 11.6;
    private static final double VISOR_BOTTOM = 19.2;

    private static final double[] FIN_ANGLES = {-48, -16, 16, 48};
    private static final int MAIN_FIN = 1;
    private static final double FIN_LENGTH = 6.2;

    private static final double FIN_LIFT = 1.6;

    private static final double RING_RADIUS = 21.6;
    private static final double RING_WIDTH = 2;

    private static final double FIT_IN_RING = 0.78;

    private static final double LIFT_IN_RING = 2.4;

    private Logo() {
    }

    private static final double SIDEBAR_SIZE = 76;
    private static final String VERSION_PROPERTY = "jpackage.app-version";

    public static Node create() {
        Group drawing = mark();
        drawing.getTransforms().add(new Scale(SIDEBAR_SIZE / CANVAS, SIDEBAR_SIZE / CANVAS, 0, 0));
        Group mark = new Group(drawing);
        mark.getStyleClass().add("logo-mark");

        Label top = new Label("Spiral");
        top.getStyleClass().add("logo-word");
        Label bottom = new Label("Captain");
        bottom.getStyleClass().add("logo-word");
        VBox words = new VBox(-9, top, bottom);
        words.setAlignment(Pos.CENTER_LEFT);

        VBox column = new VBox(words);
        String version = System.getProperty(VERSION_PROPERTY);
        if (version != null && !version.isBlank()) {
            Label label = new Label("v" + version);
            label.getStyleClass().add("logo-version");
            column.getChildren().add(label);
        }
        column.setAlignment(Pos.CENTER_LEFT);

        HBox logo = new HBox(12, mark, column);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.getStyleClass().add("logo");
        return logo;
    }

    public static List<Image> windowIcons() {
        if (windowIcons == null) {
            List<Image> icons = new ArrayList<>();
            for (int size : ICON_SIZES) {
                URL png = Logo.class.getResource("/icons/icon-" + size + ".png");
                if (png != null) {
                    icons.add(new Image(png.toExternalForm()));
                }
            }
            windowIcons = List.copyOf(icons);
        }
        return windowIcons;
    }

    private static Circle ring() {
        Circle ring = new Circle(24, 24, RING_RADIUS - RING_WIDTH / 2);
        ring.setStrokeWidth(RING_WIDTH);
        ring.getStyleClass().add("logo-ring");
        return ring;
    }

    private static Group mark() {
        Group drawing = new Group();
        for (int index = 0; index < FIN_ANGLES.length; index++) {
            SVGPath fin = fin(FIN_ANGLES[index]);
            fin.setStrokeWidth(0.7);
            fin.setStrokeLineJoin(StrokeLineJoin.ROUND);
            fin.getStyleClass().add(index == MAIN_FIN ? "logo-fin-main" : "logo-fin");
            drawing.getChildren().add(fin);
        }
        Shape helm = helm();
        helm.getStyleClass().add("logo-helm");
        drawing.getChildren().add(helm);

        double top = DOME_TOP - FIN_LENGTH - FIN_LIFT - 1;
        double width = 25.5;
        double room = 2 * (RING_RADIUS - RING_WIDTH) * FIT_IN_RING;
        double scale = Math.min(room / width, room / (BOTTOM - top));
        drawing.getTransforms().add(new Translate(24 - 12 * scale,
                24 - LIFT_IN_RING - (top + BOTTOM) / 2 * scale));
        drawing.getTransforms().add(new Scale(scale, scale, 0, 0));
        return new Group(ring(), drawing);
    }

    private static Shape helm() {
        double control = DOME_TOP + (SHOULDER - DOME_TOP) * 0.38;
        SVGPath shell = new SVGPath();
        shell.setContent(format("M3 %s C3 %s 7 %s 12 %s C17 %s 21 %s 21 %s V%s H15 V%s H9 V%s H3 Z",
                SHOULDER, control, DOME_TOP, DOME_TOP, DOME_TOP, control, SHOULDER, BOTTOM,
                GAP_TOP, BOTTOM));
        SVGPath visor = new SVGPath();
        visor.setContent(format("M7.6 %s H10.8 V%s H13.2 V%s H16.4 V%s C16.4 %s 15.7 %s 14.8 %s"
                        + " H9.2 C8.3 %s 7.6 %s 7.6 %s Z",
                VISOR_TOP, NOTCH_BOTTOM, VISOR_TOP, VISOR_BOTTOM - 1.4, VISOR_BOTTOM - 0.5,
                VISOR_BOTTOM, VISOR_BOTTOM, VISOR_BOTTOM, VISOR_BOTTOM - 0.5, VISOR_BOTTOM - 1.4));
        return Shape.subtract(shell, visor);
    }

    private static SVGPath fin(double degrees) {
        double theta = Math.toRadians(degrees);
        double dx = Math.sin(theta);
        double dy = -Math.cos(theta);
        double baseX = 12 + (8.6 + FIN_LIFT) * dx;
        double baseY = SHOULDER + (SHOULDER - DOME_TOP) * 0.96 * dy + FIN_LIFT * dy;
        double sideX = baseX + 2.6 * dx;
        double sideY = baseY + 2.6 * dy;
        double acrossX = -dy * 2.1;
        double acrossY = dx * 2.1;
        SVGPath fin = new SVGPath();
        fin.setContent(format("M%s %s L%s %s L%s %s L%s %s Z",
                baseX - dx, baseY - dy, sideX + acrossX, sideY + acrossY,
                baseX + FIN_LENGTH * dx, baseY + FIN_LENGTH * dy,
                sideX - acrossX, sideY - acrossY));
        return fin;
    }

    private static String format(String pattern, double... values) {
        Object[] text = new Object[values.length];
        for (int index = 0; index < values.length; index++) {
            text[index] = String.format(Locale.ROOT, "%.2f", values[index]);
        }
        return String.format(Locale.ROOT, pattern, text);
    }
}

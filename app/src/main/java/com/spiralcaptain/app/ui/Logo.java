package com.spiralcaptain.app.ui;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.SnapshotParameters;
import javafx.scene.control.Label;
import javafx.geometry.Rectangle2D;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.Shape;
import javafx.scene.shape.StrokeLineJoin;
import javafx.scene.transform.Scale;
import javafx.scene.transform.Translate;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class Logo {

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
    private static final String VERSION = "v1.0.0";

    public static Node create() {
        Group drawing = mark(null);
        drawing.getTransforms().add(new Scale(SIDEBAR_SIZE / CANVAS, SIDEBAR_SIZE / CANVAS, 0, 0));
        Group mark = new Group(drawing);
        mark.getStyleClass().add("logo-mark");

        Label top = new Label("Spiral");
        top.getStyleClass().add("logo-word");
        Label bottom = new Label("Captain");
        bottom.getStyleClass().add("logo-word");
        VBox words = new VBox(-9, top, bottom);
        words.setAlignment(Pos.CENTER_LEFT);

        Label version = new Label(VERSION);
        version.getStyleClass().add("logo-version");
        VBox column = new VBox(words, version);
        column.setAlignment(Pos.CENTER_LEFT);

        HBox logo = new HBox(12, mark, column);
        logo.setAlignment(Pos.CENTER_LEFT);
        logo.getStyleClass().add("logo");
        return logo;
    }

    public static List<Image> windowIcons() {
        if (windowIcons == null) {
            windowIcons = icons(16, 24, 32, 48, 64, 128);
        }
        return windowIcons;
    }

    public static List<Image> icons(int... sizes) {
        List<Image> icons = new ArrayList<>();
        Palette palette = new Palette(Color.web("#f5c400"), Color.web("#4f8fbd"),
                Color.web("#e4f2ff"), Color.web("#e4f2ff"), Color.web("#11263a"));
        for (int size : sizes) {
            icons.add(icon(palette, size));
        }
        return icons;
    }

    private static Image icon(Palette palette, int size) {
        double margin = Math.max(0.5, size / 32.0);
        double scale = (size - 2 * margin) / (2 * RING_RADIUS);
        Group sized = new Group(mark(palette));
        sized.getTransforms().add(new Translate(size / 2.0 - 24 * scale,
                size / 2.0 - 24 * scale));
        sized.getTransforms().add(new Scale(scale, scale, 0, 0));
        SnapshotParameters parameters = new SnapshotParameters();
        parameters.setFill(Color.TRANSPARENT);
        parameters.setViewport(new Rectangle2D(0, 0, size, size));
        WritableImage snapshot = sized.snapshot(parameters, new WritableImage(size, size));
        BufferedImage pixels = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                pixels.setRGB(x, y, snapshot.getPixelReader().getArgb(x, y));
            }
        }
        try {
            ByteArrayOutputStream png = new ByteArrayOutputStream();
            ImageIO.write(pixels, "png", png);
            return new Image(new ByteArrayInputStream(png.toByteArray()));
        } catch (IOException ioe) {
            return snapshot;
        }
    }

    private record Palette(Paint main, Paint follower, Paint helm, Paint line, Paint disc) { }

    private static Circle ring(Palette palette) {
        Circle ring = new Circle(24, 24, RING_RADIUS - RING_WIDTH / 2);
        ring.setStrokeWidth(RING_WIDTH);
        if (palette == null) {
            ring.getStyleClass().add("logo-ring");
        } else {
            ring.setStroke(palette.main());
            ring.setFill(palette.disc());
        }
        return ring;
    }

    private static Group mark(Palette palette) {
        Group drawing = new Group();
        for (int index = 0; index < FIN_ANGLES.length; index++) {
            SVGPath fin = fin(FIN_ANGLES[index]);
            fin.setStrokeWidth(0.7);
            fin.setStrokeLineJoin(StrokeLineJoin.ROUND);
            boolean main = index == MAIN_FIN;
            if (palette == null) {
                fin.getStyleClass().add(main ? "logo-fin-main" : "logo-fin");
            } else {
                fin.setFill(main ? palette.main() : palette.follower());
                fin.setStroke(palette.line());
            }
            drawing.getChildren().add(fin);
        }
        Shape helm = helm();
        if (palette == null) {
            helm.getStyleClass().add("logo-helm");
        } else {
            helm.setFill(palette.helm());
            helm.setStroke(null);
        }
        drawing.getChildren().add(helm);

        double top = DOME_TOP - FIN_LENGTH - FIN_LIFT - 1;
        double width = 25.5;
        double room = 2 * (RING_RADIUS - RING_WIDTH) * FIT_IN_RING;
        double scale = Math.min(room / width, room / (BOTTOM - top));
        drawing.getTransforms().add(new Translate(24 - 12 * scale,
                24 - LIFT_IN_RING - (top + BOTTOM) / 2 * scale));
        drawing.getTransforms().add(new Scale(scale, scale, 0, 0));
        return new Group(ring(palette), drawing);
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

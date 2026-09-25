package com.spiralcaptain.app.model;

import com.spiralcaptain.common.Placement;

import java.util.ArrayList;
import java.util.List;

import static com.spiralcaptain.app.model.Arrangement.box;

public enum Layout implements Arrangement {

    DIAGONAL("Diagonal"),
    THREE_CORNERS("Three corners"),
    FOUR_CORNERS("Four corners"),
    SIDE_BY_SIDE("Side by side"),
    THREE_ACROSS("Three across"),
    CASCADE("Cascade left"),
    CASCADE_RIGHT("Cascade right"),
    TWO_MONITORS("Two monitors");

    public static final String KEY_PREFIX = "layout:";

    private static final int CASCADE_STEPS = 3;
    private static final float CASCADE_STEP = 0.045f;
    private static final int CASCADE_MIN_STEP = 24;

    private final String label;

    Layout(String label) {
        this.label = label;
    }

    @Override
    public String label() {
        return label;
    }

    @Override
    public String key() {
        return KEY_PREFIX + name();
    }

    @Override
    public int screens() {
        return this == TWO_MONITORS ? 2 : 1;
    }

    @Override
    public boolean maximized(int slot) {
        return slot == 0 && this == TWO_MONITORS;
    }

    @Override
    public boolean stacked() {
        return this == CASCADE || this == CASCADE_RIGHT;
    }

    @Override
    public List<Placement> placements(List<Screen> screens) {
        int[] area = screens.getFirst().area();
        int x = area[0];
        int y = area[1];
        int width = area[2];
        int height = area[3];
        return switch (this) {
            case DIAGONAL -> {
                int span = Math.round(width * 2f / 3f);
                yield List.of(
                        box(x, y, span, height, false, false, true),
                        box(x + width - span, y, span, height, true, true, true));
            }
            case THREE_CORNERS -> quarters(area).subList(0, 3);
            case FOUR_CORNERS -> quarters(area);
            case SIDE_BY_SIDE -> {
                int half = width / 2;
                yield List.of(
                        box(x, y, half, height, false, false, true),
                        box(x + half, y, width - half, height, true, false, true));
            }
            case THREE_ACROSS -> {
                int third = width / 3;
                yield List.of(
                        box(x, y, third, height, false, false, true),
                        box(x + third, y, third, height, false, false, true),
                        box(x + 2 * third, y, width - 2 * third, height, true, false, true));
            }
            case CASCADE -> cascade(area, false);
            case CASCADE_RIGHT -> cascade(area, true);
            case TWO_MONITORS -> {
                List<Placement> places = new ArrayList<>();
                places.add(box(x, y, width, height, false, false, false));
                places.addAll(quarters(screens.size() > 1 ? screens.get(1).area() : area));
                yield places;
            }
        };
    }

    private static List<Placement> cascade(int[] area, boolean toRight) {
        int x = area[0];
        int y = area[1];
        int width = area[2];
        int height = area[3];
        int step = Math.max(CASCADE_MIN_STEP, Math.round(height * CASCADE_STEP));
        int boxWidth = width - CASCADE_STEPS * step;
        int boxHeight = height - CASCADE_STEPS * step;
        List<Placement> places = new ArrayList<>();
        for (int slot = 0; slot <= CASCADE_STEPS; slot++) {
            int offset = (slot == 0 ? CASCADE_STEPS : CASCADE_STEPS - slot) * step;
            int left = toRight ? x + CASCADE_STEPS * step - offset : x + offset;
            places.add(box(left, y + offset, boxWidth, boxHeight, toRight, false, true));
        }
        return places;
    }

    private static List<Placement> quarters(int[] area) {
        int x = area[0];
        int y = area[1];
        int width = area[2];
        int height = area[3];
        int left = width / 2;
        int top = height / 2;
        return List.of(
                box(x, y, left, top, false, false, false),
                box(x + left, y, width - left, top, true, false, false),
                box(x, y + top, left, height - top, false, true, false),
                box(x + left, y + top, width - left, height - top, true, true, false));
    }
}

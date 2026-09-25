package com.spiralcaptain.app.model;

import com.spiralcaptain.common.Placement;

import java.util.List;

public interface Arrangement {

    int GAME_MIN_WIDTH = 1024;
    int GAME_MIN_HEIGHT = 600;

    String label();

    String key();

    List<Placement> placements(List<Screen> screens);

    default int screens() {
        return 1;
    }

    default boolean maximized(int slot) {
        return false;
    }

    default boolean stacked() {
        return false;
    }

    static Placement box(int x, int y, int width, int height, boolean right, boolean bottom,
            boolean widescreen) {
        return new Placement(x, y, width, height, right, bottom, widescreen, GAME_MIN_WIDTH,
                GAME_MIN_HEIGHT);
    }
}

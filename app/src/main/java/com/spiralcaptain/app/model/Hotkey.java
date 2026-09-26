package com.spiralcaptain.app.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public record Hotkey(boolean ctrl, boolean alt, boolean shift, int key) {

    public static final int DEFAULT_COUNT = 5;

    private static final int MOD_ALT = 0x0001;
    private static final int MOD_CONTROL = 0x0002;
    private static final int MOD_SHIFT = 0x0004;
    private static final int MOD_NOREPEAT = 0x4000;

    private static final int DIGIT_0 = 0x30;
    private static final int LETTER_A = 0x41;
    private static final int NUMPAD_0 = 0x60;
    private static final int F1 = 0x70;
    private static final int LAST_KEY = 0xFF;

    public static Optional<Hotkey> defaultFor(int number) {
        return number >= 1 && number <= DEFAULT_COUNT
                ? Optional.of(new Hotkey(false, true, false, DIGIT_0 + number))
                : Optional.empty();
    }

    public static Optional<Hotkey> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        String[] parts = text.split("\\+");
        boolean ctrl = false;
        boolean alt = false;
        boolean shift = false;
        for (int index = 0; index < parts.length - 1; index++) {
            switch (parts[index].trim().toLowerCase(Locale.ROOT)) {
                case "ctrl" -> ctrl = true;
                case "alt" -> alt = true;
                case "shift" -> shift = true;
                default -> {
                    return Optional.empty();
                }
            }
        }
        String last = parts[parts.length - 1].trim();
        for (int key = 0; key <= LAST_KEY; key++) {
            if (last.equalsIgnoreCase(keyName(key))) {
                Hotkey hotkey = new Hotkey(ctrl, alt, shift, key);
                return hotkey.usable() ? Optional.of(hotkey) : Optional.empty();
            }
        }
        return Optional.empty();
    }

    public boolean usable() {
        return (ctrl || alt) && keyName(key) != null;
    }

    public int modifiers() {
        return (ctrl ? MOD_CONTROL : 0) | (alt ? MOD_ALT : 0) | (shift ? MOD_SHIFT : 0)
                | MOD_NOREPEAT;
    }

    public String text() {
        List<String> parts = new ArrayList<>();
        if (ctrl) {
            parts.add("Ctrl");
        }
        if (alt) {
            parts.add("Alt");
        }
        if (shift) {
            parts.add("Shift");
        }
        parts.add(keyName(key) == null ? "?" : keyName(key));
        return String.join("+", parts);
    }

    private static String keyName(int key) {
        if (key >= DIGIT_0 && key <= DIGIT_0 + 9) {
            return String.valueOf((char) key);
        }
        if (key >= LETTER_A && key < LETTER_A + 26) {
            return String.valueOf((char) key);
        }
        if (key >= NUMPAD_0 && key <= NUMPAD_0 + 9) {
            return "Num " + (key - NUMPAD_0);
        }
        if (key >= F1 && key < F1 + 12) {
            return "F" + (key - F1 + 1);
        }
        return null;
    }
}

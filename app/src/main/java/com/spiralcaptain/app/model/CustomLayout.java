package com.spiralcaptain.app.model;

import com.spiralcaptain.common.Placement;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public record CustomLayout(String id, String name, List<Slot> slots) implements Arrangement {

    public record Slot(double x, double y, double width, double height, boolean maximized,
            String monitor) {

        public Slot {
            monitor = monitor == null ? "" : monitor;
        }

        String encode() {
            return String.format(Locale.ROOT, "%.5f,%.5f,%.5f,%.5f,%s,%s", x, y, width, height,
                    maximized ? "max" : "normal", monitor);
        }

        static Slot decode(String text) {
            String[] parts = text.split(",");
            return new Slot(Double.parseDouble(parts[0]), Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]), Double.parseDouble(parts[3]),
                    parts.length > 4 && parts[4].equals("max"),
                    parts.length > 5 ? parts[5].trim() : "");
        }
    }

    public CustomLayout {
        slots = List.copyOf(slots);
    }

    public static CustomLayout create(String name, List<Slot> slots) {
        return new CustomLayout(UUID.randomUUID().toString(), name, slots);
    }

    public CustomLayout named(String newName) {
        return new CustomLayout(id, newName, slots);
    }

    @Override
    public String label() {
        return name;
    }

    @Override
    public String key() {
        return "custom:" + id;
    }

    @Override
    public List<Placement> placements(List<Screen> screens) {
        Screen target = screens.getFirst();
        boolean spans = spansMonitors();
        List<Placement> placements = new ArrayList<>();
        for (Slot slot : slots) {
            Screen home = spans
                    ? screens.stream()
                            .filter(screen -> screen.device().equals(slot.monitor()))
                            .findFirst()
                            .orElse(target)
                    : target;
            int x = home.x();
            int y = home.y();
            int width = home.width();
            int height = home.height();
            int left = x + (int) Math.round(slot.x() * width);
            int top = y + (int) Math.round(slot.y() * height);
            int boxWidth = Math.max(1, (int) Math.round(slot.width() * width));
            int boxHeight = Math.max(1, (int) Math.round(slot.height() * height));
            boolean right = slot.x() + slot.width() / 2 > 0.5;
            boolean bottom = slot.y() + slot.height() / 2 > 0.5;
            placements.add(Arrangement.box(left, top, boxWidth, boxHeight, right, bottom,
                    false));
        }
        return placements;
    }

    @Override
    public boolean maximized(int slot) {
        return slot >= 0 && slot < slots.size() && slots.get(slot).maximized();
    }

    public boolean spansMonitors() {
        return slots.stream().map(Slot::monitor).distinct().count() > 1;
    }

    public String encodeSlots() {
        return String.join(";", slots.stream().map(Slot::encode).toList());
    }

    public static List<Slot> decodeSlots(String text) {
        List<Slot> slots = new ArrayList<>();
        for (String part : text.split(";")) {
            if (!part.isBlank()) {
                slots.add(Slot.decode(part.trim()));
            }
        }
        return slots;
    }
}

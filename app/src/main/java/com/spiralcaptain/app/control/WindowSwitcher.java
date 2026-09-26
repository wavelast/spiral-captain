package com.spiralcaptain.app.control;

import com.spiralcaptain.app.launch.WindowHotkeys;
import com.spiralcaptain.app.model.Account;
import com.spiralcaptain.app.model.Hotkey;
import com.spiralcaptain.app.store.Settings;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

public final class WindowSwitcher {

    private final Settings settings;
    private final Fleet fleet;
    private final Consumer<String> alerts;
    private final Set<Hotkey> warned = new HashSet<>();
    private volatile List<Account> order = List.of();
    private Map<Integer, Hotkey> active = Map.of();
    private WindowHotkeys hotkeys;
    private boolean suspended;

    public WindowSwitcher(Settings settings, Fleet fleet, Consumer<String> alerts) {
        this.settings = settings;
        this.fleet = fleet;
        this.alerts = alerts;
    }

    public void update(List<Account> accounts) {
        order = List.copyOf(accounts);
        Map<Integer, Hotkey> wanted = wanted(accounts.size());
        if (wanted.equals(active)) {
            return;
        }
        stop();
        active = wanted;
        if (wanted.isEmpty()) {
            return;
        }
        hotkeys = WindowHotkeys.start(wanted, this::pressed);
        List<String> taken = new ArrayList<>();
        for (int number : hotkeys.failed()) {
            Hotkey hotkey = wanted.get(number);
            if (warned.add(hotkey)) {
                taken.add(hotkey.text());
            }
        }
        if (!taken.isEmpty()) {
            alerts.accept(String.join(" and ", taken)
                    + (taken.size() == 1 ? " is" : " are")
                    + " already used by another program, so "
                    + (taken.size() == 1 ? "it" : "they")
                    + " cannot switch game windows. Choose another on the Hotkeys page.");
        }
    }

    public void suspend(boolean suspend) {
        suspended = suspend;
        update(order);
    }

    public void shutdown() {
        stop();
        active = Map.of();
    }

    private Map<Integer, Hotkey> wanted(int count) {
        Map<Integer, Hotkey> wanted = new LinkedHashMap<>();
        if (suspended || !settings.windowHotkeys() || !fleet.anyRunning()) {
            return wanted;
        }
        for (int number = 1; number <= count; number++) {
            Optional<Hotkey> hotkey = settings.windowHotkey(number);
            if (hotkey.isPresent() && !wanted.containsValue(hotkey.get())) {
                wanted.put(number, hotkey.get());
            }
        }
        return wanted;
    }

    private void pressed(int number) {
        List<Account> accounts = order;
        if (number >= 1 && number <= accounts.size()) {
            fleet.bringToFront(accounts.get(number - 1));
        }
    }

    private void stop() {
        if (hotkeys != null) {
            hotkeys.stop();
            hotkeys = null;
        }
    }
}

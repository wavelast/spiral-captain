package com.spiralcaptain.app.control;

import com.spiralcaptain.app.launch.ClientLauncher;
import com.spiralcaptain.app.launch.GameInstall;
import com.spiralcaptain.app.launch.GameWindows;
import com.spiralcaptain.app.launch.ProcessPriority;
import com.spiralcaptain.app.model.Account;
import com.spiralcaptain.app.model.Arrangement;
import com.spiralcaptain.app.model.CustomLayout;
import com.spiralcaptain.app.model.Layout;
import com.spiralcaptain.app.model.Screen;
import com.spiralcaptain.app.store.Settings;
import com.spiralcaptain.common.Placement;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public final class Fleet {

    private static final long POLL_MS = 1500L;
    private static final long ARRANGE_SETTLE_MS = 2000L;

    private final Settings settings;
    private final GameInstall install;
    private final Path prefsJar;
    private final Map<String, RunningClient> clients = new ConcurrentHashMap<>();
    private final List<String> launchOrder = new CopyOnWriteArrayList<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private final Object arrangeLock = new Object();
    private ScheduledFuture<?> pendingArrange;
    private final List<String> log = new ArrayList<>();

    private final ScheduledExecutorService background =
            Executors.newScheduledThreadPool(2, runnable -> {
                Thread thread = new Thread(runnable, "spiral-captain-fleet");
                thread.setDaemon(true);
                return thread;
            });

    public Fleet(Settings settings, GameInstall install, Path prefsJar) {
        this.settings = settings;
        this.install = install;
        this.prefsJar = prefsJar;
        background.scheduleWithFixedDelay(this::poll, POLL_MS, POLL_MS, TimeUnit.MILLISECONDS);
    }

    public void onChange(Runnable listener) {
        synchronized (listeners) {
            listeners.add(listener);
        }
    }

    private void changed() {
        List<Runnable> snapshot;
        synchronized (listeners) {
            snapshot = List.copyOf(listeners);
        }
        snapshot.forEach(Runnable::run);
    }

    public Collection<RunningClient> clients() {
        return List.copyOf(clients.values());
    }

    public Optional<RunningClient> clientFor(Account account) {
        return Optional.ofNullable(clients.get(account.id()));
    }

    public Optional<RunningClient> mainClient() {
        return clients.values().stream()
                .filter(RunningClient::main)
                .filter(RunningClient::alive)
                .findFirst();
    }

    public List<RunningClient> followers() {
        List<RunningClient> ordered = new ArrayList<>();
        for (String id : launchOrder) {
            RunningClient client = clients.get(id);
            if (client != null && client.alive() && !client.main()) {
                ordered.add(client);
            }
        }
        return ordered;
    }

    public List<String> recentLog() {
        synchronized (log) {
            return List.copyOf(log);
        }
    }

    private void note(String line) {
        synchronized (log) {
            log.add(line);
            while (log.size() > 300) {
                log.removeFirst();
            }
        }
        changed();
    }

    public boolean isRunning(Account account) {
        RunningClient client = clients.get(account.id());
        return client != null && client.alive();
    }

    public void launch(List<Account> accounts) {
        List<Account> ready = new ArrayList<>();
        for (Account account : accounts) {
            if (isRunning(account)) {
                note("Already running: " + account.displayName());
                continue;
            }
            String problem = account.launchProblem();
            if (problem != null) {
                note("Cannot launch " + account.displayName() + ": it needs " + problem);
                continue;
            }
            ready.add(account);
        }
        ready.sort(Comparator.comparing(account -> !account.main()));
        GameInstall current = GameInstall.at(install.directory());
        background.execute(() -> ready.forEach(account -> launchOne(account, current)));
    }

    public void prioritiesChanged() {
        background.execute(() -> clients.values().forEach(this::applyPriority));
    }

    private void applyPriority(RunningClient client) {
        if (client.alive()) {
            ProcessPriority.set(client.process().pid(),
                    settings.lowerFollowerPriority() && !client.main());
        }
    }

    public void report(String line) {
        note(line);
    }

    public boolean anyRunning() {
        return clients.values().stream().anyMatch(RunningClient::alive);
    }

    private void launchOne(Account account, GameInstall current) {
        try {
            Process process = new ClientLauncher(current, settings, prefsJar).launch(account);
            RunningClient client = new RunningClient(account, process);
            applyPriority(client);
            clients.put(account.id(), client);
            launchOrder.remove(account.id());
            launchOrder.add(account.id());
            note("Launched " + account.displayName());
        } catch (IOException failure) {
            note("Could not launch " + account.displayName() + ": " + failure.getMessage());
        }
    }

    private List<RunningClient> arrangeOrder() {
        List<RunningClient> order = new ArrayList<>();
        mainClient().ifPresent(order::add);
        order.addAll(followers());
        return order;
    }

    private GameWindows.Monitor targetMonitor(String monitorDevice, RunningClient reference,
            boolean quiet) {
        if (monitorDevice != null && !monitorDevice.isEmpty()) {
            List<GameWindows.Monitor> connected = GameWindows.monitors();
            Optional<GameWindows.Monitor> chosen = connected.stream()
                    .filter(monitor -> monitor.device().equals(monitorDevice))
                    .findFirst();
            if (chosen.isPresent() && connected.size() > 1) {
                return chosen.get();
            }
            if (!quiet && chosen.isEmpty()) {
                note("That monitor is not connected now, so the main client's monitor was used");
            }
        }
        return GameWindows.monitorOf(reference.window());
    }

    public Optional<Arrangement> rememberedLayout() {
        String key = settings.lastLayout();
        if (key.startsWith(Layout.KEY_PREFIX)) {
            try {
                return Optional.of(Layout.valueOf(key.substring(Layout.KEY_PREFIX.length())));
            } catch (IllegalArgumentException removed) {
                return Optional.empty();
            }
        }
        return settings.customLayouts().stream()
                .filter(layout -> layout.key().equals(key))
                .map(Arrangement.class::cast)
                .findFirst();
    }

    private void arrangeSoon() {
        synchronized (arrangeLock) {
            if (pendingArrange != null) {
                pendingArrange.cancel(false);
            }
            pendingArrange = background.schedule(() -> rememberedLayout().ifPresent(layout ->
                    arrangeNow(layout, settings.arrangeMonitor(), true)),
                    ARRANGE_SETTLE_MS, TimeUnit.MILLISECONDS);
        }
    }

    public void arrange(Arrangement arrangement, String monitorDevice) {
        background.execute(() -> arrangeNow(arrangement, monitorDevice, false));
    }

    private void arrangeNow(Arrangement arrangement, String monitorDevice, boolean quiet) {
        synchronized (arrangeLock) {
            List<RunningClient> order = arrangeOrder();
            RunningClient reference = order.stream()
                    .filter(RunningClient::hasWindow)
                    .findFirst()
                    .orElse(null);
            if (reference == null) {
                if (!quiet) {
                    note(order.isEmpty()
                            ? arrangement.label() + " will be used at launch"
                            : "The game windows have not opened yet; " + arrangement.label()
                                    + " will be used when they do");
                }
                return;
            }
            GameWindows.Monitor target = targetMonitor(monitorDevice, reference, quiet);
            List<Screen> screens = new ArrayList<>();
            screens.add(target.screen());
            GameWindows.monitors().stream()
                    .filter(monitor -> !monitor.device().equals(target.device()))
                    .map(GameWindows.Monitor::screen)
                    .forEach(screens::add);
            if (arrangement.screens() > screens.size()) {
                note(arrangement.label() + " needs a second monitor");
                return;
            }
            List<Placement> places = arrangement.placements(screens);
            List<Long> placed = new ArrayList<>();
            int arranged = 0;
            for (int slot = 0; slot < order.size() && slot < places.size(); slot++) {
                RunningClient client = order.get(slot);
                String name = client.account().displayName();
                if (!client.hasWindow()) {
                    if (!quiet) {
                        note(name + " is still starting, so its window was left alone");
                    }
                    continue;
                }
                String problem = GameWindows.place(client.window(), places.get(slot));
                if (problem != null) {
                    note(name + " was left where it was: " + problem);
                    continue;
                }
                if (arrangement.maximized(slot)) {
                    GameWindows.maximize(client.window());
                }
                placed.add(client.window());
                arranged++;
            }
            if (arrangement.stacked() && !placed.isEmpty()) {
                if (!quiet) {
                    GameWindows.activate(placed.getFirst());
                }
                for (int index = 1; index < placed.size(); index++) {
                    GameWindows.placeBelow(placed.get(index), placed.get(index - 1));
                }
            }
            if (order.size() > places.size() && !quiet) {
                int extra = order.size() - places.size();
                note(extra + (extra == 1 ? " client was" : " clients were")
                        + " left where they were: " + arrangement.label() + " has room for "
                        + places.size());
            }
            note(quiet
                    ? "Arranged " + arranged + (arranged == 1 ? " window" : " windows") + " for "
                            + arrangement.label()
                    : "Arranged " + arranged + (arranged == 1 ? " window" : " windows") + ". "
                            + arrangement.label() + " will be used at launch");
        }
    }

    public void capture(String name, Consumer<CustomLayout> saved) {
        background.execute(() -> {
            List<RunningClient> order = arrangeOrder();
            if (order.isEmpty()) {
                note("Launch the clients and place their windows first");
                return;
            }
            for (RunningClient client : order) {
                String who = client.account().displayName();
                if (!client.hasWindow()) {
                    note(who + " is still starting; save once every window is open");
                    return;
                }
                if (GameWindows.minimized(client.window())) {
                    note(who + " is minimized; restore it before saving");
                    return;
                }
                if (!GameWindows.framed(client.window())) {
                    note(who + " is in full screen; switch it to a window before saving");
                    return;
                }
            }
            List<CustomLayout.Slot> slots = new ArrayList<>();
            for (RunningClient client : order) {
                GameWindows.Monitor monitor = GameWindows.monitorOf(client.window());
                int[] area = monitor.workArea();
                int[] bounds = GameWindows.visibleBounds(client.window());
                slots.add(new CustomLayout.Slot(
                        (bounds[0] - area[0]) / (double) area[2],
                        (bounds[1] - area[1]) / (double) area[3],
                        bounds[2] / (double) area[2],
                        bounds[3] / (double) area[3],
                        GameWindows.maximized(client.window()),
                        monitor.device()));
            }
            CustomLayout layout = CustomLayout.create(name, slots);
            saved.accept(layout);
            note("Saved " + slots.size() + (slots.size() == 1 ? " window" : " windows")
                    + " as " + name);
        });
    }

    public void makeMain(Account account) {
        background.execute(() -> {
            RunningClient target = clients.get(account.id());
            clients.values().forEach(client -> client.main(client == target));
            clients.values().forEach(this::applyPriority);
            if (target != null) {
                note(target.account().displayName() + " is now the main client");
            }
            changed();
        });
    }

    public void quit(RunningClient client) {
        background.execute(() -> {
            if (client.hasWindow() && GameWindows.exists(client.window())) {
                GameWindows.close(client.window());
            } else {
                client.process().destroy();
            }
            note("Closing " + client.account().displayName());
        });
    }

    public void quitFollowers() {
        followers().forEach(this::quit);
    }

    public void quitAll() {
        clients.values().stream().filter(RunningClient::alive).forEach(this::quit);
    }

    private void poll() {
        boolean anyChange = false;
        for (RunningClient client : clients.values()) {
            if (!client.alive()) {
                clients.remove(client.account().id(), client);
                launchOrder.remove(client.account().id());
                note(client.account().displayName() + " closed");
                anyChange = true;
                continue;
            }
            if (client.hasWindow()) {
                continue;
            }
            Optional<Long> window = GameWindows.find(client.process().pid());
            if (window.isPresent()) {
                client.window(window.get());
                anyChange = true;
                arrangeSoon();
            }
        }
        if (anyChange) {
            changed();
        }
    }

    public void shutdown() {
        background.shutdownNow();
    }
}

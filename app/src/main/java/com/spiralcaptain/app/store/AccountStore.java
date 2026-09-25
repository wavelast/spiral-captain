package com.spiralcaptain.app.store;

import com.spiralcaptain.app.model.Account;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

public final class AccountStore {

    private static final String FILE_NAME = "accounts.properties";
    private static final String ORDER_KEY = "accounts";

    private final Path file;

    public AccountStore() {
        this(defaultDirectory().resolve(FILE_NAME));
    }

    public AccountStore(Path file) {
        this.file = file;
    }

    public static final String HOME_PROPERTY = "spiralcaptain.home";

    public static Path defaultDirectory() {
        String home = System.getProperty(HOME_PROPERTY);
        if (home != null && !home.isBlank()) {
            return Path.of(home);
        }
        String appData = System.getenv("APPDATA");
        Path base = appData != null && !appData.isBlank()
                ? Path.of(appData)
                : Path.of(System.getProperty("user.home", "."));
        return base.resolve(appData != null && !appData.isBlank()
                ? "SpiralCaptain" : ".spiral-captain");
    }

    public Path file() {
        return file;
    }

    public List<Account> load() {
        List<Account> accounts = new ArrayList<>();
        if (!Files.isReadable(file)) {
            return accounts;
        }
        Properties stored = new Properties();
        try (InputStream in = Files.newInputStream(file)) {
            stored.load(in);
        } catch (IOException ioe) {
            throw new StoreException("Cannot read " + file, ioe);
        }
        for (String id : ids(stored)) {
            Account account = new Account(id);
            account.accountName(stored.getProperty(id + ".accountName", ""));
            account.knightName(readKnight(stored, id));
            account.passwordDigest(Secrets.unseal(stored.getProperty(id + ".password", "")));
            account.loginMode(readMode(stored.getProperty(id + ".loginMode", "")));
            account.selected(Boolean.parseBoolean(stored.getProperty(id + ".selected", "true")));
            accounts.add(account);
        }
        for (int index = 0; index < accounts.size(); index++) {
            accounts.get(index).main(index == 0);
        }
        return accounts;
    }

    public void save(List<Account> accounts) {
        Properties stored = new Properties();
        StringBuilder order = new StringBuilder();
        for (Account account : accounts) {
            if (!order.isEmpty()) {
                order.append(',');
            }
            order.append(account.id());
            String id = account.id();
            stored.setProperty(id + ".accountName", account.accountName());
            stored.setProperty(id + ".knight", account.knightName());
            stored.setProperty(id + ".password", Secrets.seal(account.passwordDigest()));
            stored.setProperty(id + ".loginMode", account.loginMode().name());
            stored.setProperty(id + ".selected", Boolean.toString(account.selected()));
        }
        stored.setProperty(ORDER_KEY, order.toString());
        try {
            Files.createDirectories(file.getParent());
            Path temporary = file.resolveSibling(file.getFileName() + ".new");
            try (OutputStream out = Files.newOutputStream(temporary)) {
                stored.store(out, "Spiral Captain accounts. Password digests are sealed, not plain.");
            }
            Files.move(temporary, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ioe) {
            throw new StoreException("Cannot write " + file, ioe);
        }
    }

    private static Set<String> ids(Properties stored) {
        Set<String> ids = new LinkedHashSet<>();
        String order = stored.getProperty(ORDER_KEY, "");
        for (String id : order.split(",")) {
            String trimmed = id.trim();
            if (!trimmed.isEmpty()) {
                ids.add(trimmed);
            }
        }
        for (String key : stored.stringPropertyNames()) {
            int dot = key.indexOf('.');
            if (dot > 0) {
                ids.add(key.substring(0, dot));
            }
        }
        return ids;
    }

    private static String readKnight(Properties stored, String id) {
        String knight = stored.getProperty(id + ".knight");
        if (knight != null) {
            return knight;
        }
        String[] knights = stored.getProperty(id + ".knights", "").split(",", -1);
        int slot;
        try {
            slot = Integer.parseInt(stored.getProperty(id + ".knightSlot", "0").trim());
        } catch (NumberFormatException nfe) {
            slot = 0;
        }
        return slot >= 0 && slot < knights.length ? knights[slot] : "";
    }

    private static Account.LoginMode readMode(String stored) {
        try {
            return stored.isEmpty() ? Account.LoginMode.PASSWORD
                    : Account.LoginMode.valueOf(stored);
        } catch (IllegalArgumentException iae) {
            return Account.LoginMode.PASSWORD;
        }
    }

    public static class StoreException extends RuntimeException {
        public StoreException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

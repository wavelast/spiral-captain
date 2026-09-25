package com.spiralcaptain.app.model;

import java.util.UUID;

public final class Account {

    public enum LoginMode {

        STEAM("Steam"),

        PASSWORD("Username");

        private final String label;

        LoginMode(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final String id;
    private String accountName = "";
    private String knightName = "";
    private String passwordDigest = "";
    private LoginMode loginMode = LoginMode.PASSWORD;
    private boolean selected = true;

    private boolean main;

    public Account() {
        this(UUID.randomUUID().toString());
    }

    public Account(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    public String accountName() {
        return accountName;
    }

    public void accountName(String accountName) {
        this.accountName = accountName == null ? "" : accountName.trim();
    }

    public String knightName() {
        return knightName;
    }

    public void knightName(String knightName) {
        this.knightName = knightName == null ? "" : knightName.trim();
    }

    public String passwordDigest() {
        return passwordDigest;
    }

    public void passwordDigest(String passwordDigest) {
        this.passwordDigest = passwordDigest == null ? "" : passwordDigest.trim();
    }

    public LoginMode loginMode() {
        return loginMode;
    }

    public void loginMode(LoginMode loginMode) {
        this.loginMode = loginMode == null ? LoginMode.PASSWORD : loginMode;
    }

    public boolean main() {
        return main;
    }

    public void main(boolean main) {
        this.main = main;
    }

    public boolean selected() {
        return selected;
    }

    public void selected(boolean selected) {
        this.selected = selected;
    }

    public String launchProblem() {
        if (loginMode == LoginMode.STEAM) {
            return null;
        }
        if (accountName.isEmpty()) {
            return "an account name";
        }
        if (passwordDigest.isEmpty()) {
            return "a password";
        }
        return null;
    }

    public String displayName() {
        if (!knightName().isEmpty()) {
            return knightName();
        }
        return accountName.isEmpty() ? "(unnamed)" : accountName;
    }

    @Override
    public String toString() {
        return displayName();
    }
}

package com.spiralcaptain.app.control;

import com.spiralcaptain.app.model.Account;

public final class RunningClient {

    public enum State {

        STARTING("Starting"),

        RUNNING("Running"),

        STOPPED("Stopped");

        private final String label;

        State(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final Account account;
    private final Process process;
    private volatile long window;
    private volatile boolean main;

    RunningClient(Account account, Process process) {
        this.account = account;
        this.process = process;
        this.main = account.main();
    }

    public Account account() {
        return account;
    }

    public Process process() {
        return process;
    }

    public long window() {
        return window;
    }

    void window(long window) {
        this.window = window;
    }

    public boolean hasWindow() {
        return window != 0L;
    }

    public boolean main() {
        return main;
    }

    void main(boolean main) {
        this.main = main;
    }

    public boolean alive() {
        return process.isAlive();
    }

    public State state() {
        if (!alive()) {
            return State.STOPPED;
        }
        return hasWindow() ? State.RUNNING : State.STARTING;
    }
}

package com.spiralcaptain.app;

import com.spiralcaptain.app.store.AccountStore;

import java.util.ArrayList;
import java.util.List;

public final class Launcher {

    private static final String DATA_OPTION = "--data=";

    private Launcher() {
    }

    public static void main(String[] args) {
        List<String> rest = new ArrayList<>();
        for (String arg : args) {
            if (arg.startsWith(DATA_OPTION)) {
                System.setProperty(AccountStore.HOME_PROPERTY, arg.substring(DATA_OPTION.length()));
            } else {
                rest.add(arg);
            }
        }
        SpiralCaptainApp.main(rest.toArray(String[]::new));
    }
}

package com.spiralcaptain.app.launch;

import com.spiralcaptain.app.model.Hotkey;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

public final class WindowHotkeys {

    private static final int WM_QUIT = 0x0012;
    private static final int WM_USER = 0x0400;
    private static final int WM_HOTKEY = 0x0312;
    private static final int PM_NOREMOVE = 0;
    private static final int MSG_BYTES = 48;
    private static final int MSG_MESSAGE = 8;
    private static final int MSG_WPARAM = 16;
    private static final long WAIT_MS = 2000L;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32", Arena.global());
    private static final SymbolLookup KERNEL32 =
            SymbolLookup.libraryLookup("kernel32", Arena.global());

    private static final MethodHandle REGISTER = user32("RegisterHotKey",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle UNREGISTER = user32("UnregisterHotKey",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle GET_MESSAGE = user32("GetMessageW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle PEEK_MESSAGE = user32("PeekMessageW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle POST_THREAD = user32("PostThreadMessageW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle THREAD_ID = LINKER.downcallHandle(
            KERNEL32.find("GetCurrentThreadId").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT));

    private final Thread thread;
    private final CountDownLatch ready = new CountDownLatch(1);
    private final List<Integer> failed = new CopyOnWriteArrayList<>();
    private volatile int threadId;

    private WindowHotkeys(Map<Integer, Hotkey> keys, IntConsumer pressed) {
        thread = Thread.ofPlatform().daemon().name("spiral-captain-hotkeys")
                .start(() -> run(keys, pressed));
    }

    private static MethodHandle user32(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(USER32.find(name).orElseThrow(), descriptor);
    }

    public static WindowHotkeys start(Map<Integer, Hotkey> keys, IntConsumer pressed) {
        WindowHotkeys hotkeys = new WindowHotkeys(Map.copyOf(keys), pressed);
        try {
            hotkeys.ready.await(WAIT_MS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return hotkeys;
    }

    public List<Integer> failed() {
        return List.copyOf(failed);
    }

    public void stop() {
        if (threadId != 0) {
            try {
                int posted = (int) POST_THREAD.invokeExact(threadId, WM_QUIT, 0L, 0L);
            } catch (Throwable ignored) {
            }
        }
        try {
            thread.join(WAIT_MS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void run(Map<Integer, Hotkey> keys, IntConsumer pressed) {
        List<Integer> registered = new ArrayList<>();
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment message = arena.allocate(MSG_BYTES);
            int peeked = (int) PEEK_MESSAGE.invokeExact(message, MemorySegment.NULL, WM_USER,
                    WM_USER, PM_NOREMOVE);
            threadId = (int) THREAD_ID.invokeExact();
            for (Map.Entry<Integer, Hotkey> entry : keys.entrySet()) {
                Hotkey hotkey = entry.getValue();
                int ok = (int) REGISTER.invokeExact(MemorySegment.NULL, (int) entry.getKey(),
                        hotkey.modifiers(), hotkey.key());
                if (ok != 0) {
                    registered.add(entry.getKey());
                } else {
                    failed.add(entry.getKey());
                }
            }
            ready.countDown();
            while ((int) GET_MESSAGE.invokeExact(message, MemorySegment.NULL, 0, 0) > 0) {
                if (message.get(ValueLayout.JAVA_INT, MSG_MESSAGE) == WM_HOTKEY) {
                    try {
                        pressed.accept((int) message.get(ValueLayout.JAVA_LONG, MSG_WPARAM));
                    } catch (RuntimeException failure) {
                        System.err.println("Hotkey failed: " + failure);
                    }
                }
            }
            for (int id : registered) {
                int removed = (int) UNREGISTER.invokeExact(MemorySegment.NULL, id);
            }
        } catch (Throwable failure) {
            System.err.println("Hotkeys stopped: " + failure);
        } finally {
            ready.countDown();
        }
    }
}

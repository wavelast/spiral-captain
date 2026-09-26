package com.spiralcaptain.app.launch;

import com.spiralcaptain.app.model.Screen;
import com.spiralcaptain.common.Placement;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class GameWindows {

    private static final String WINDOW_CLASS = "GLFW30";
    private static final long PER_MONITOR_AWARE_V2 = -4L;
    private static final int GWL_STYLE = -16;
    private static final long WS_CAPTION = 0x00C00000L;
    private static final int SW_MAXIMIZE = 3;
    private static final int SW_RESTORE = 9;
    private static final int MONITOR_DEFAULTTONEAREST = 2;
    private static final int DWMWA_EXTENDED_FRAME_BOUNDS = 9;
    private static final int SWP_NOZORDER = 0x0004;
    private static final int SWP_NOACTIVATE = 0x0010;
    private static final int SWP_NOOWNERZORDER = 0x0200;
    private static final int SWP_NOSIZE = 0x0001;
    private static final int SWP_NOMOVE = 0x0002;
    private static final int WM_CLOSE = 0x0010;
    private static final int RECT_BYTES = 16;
    private static final int MONITOR_INFO_BYTES = 104;
    private static final int MONITORINFOF_PRIMARY = 1;
    private static final int MOST_PLAUSIBLE_BORDER = 64;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final Arena LIBRARIES = Arena.global();
    private static final SymbolLookup USER32 = SymbolLookup.libraryLookup("user32", LIBRARIES);
    private static final SymbolLookup DWMAPI = SymbolLookup.libraryLookup("dwmapi", LIBRARIES);

    private static final MethodHandle SET_DPI_CONTEXT = user32("SetThreadDpiAwarenessContext",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle FIND_WINDOW = user32("FindWindowExW",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle WINDOW_PROCESS = user32("GetWindowThreadProcessId",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle IS_VISIBLE = user32("IsWindowVisible",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle IS_ZOOMED = user32("IsZoomed",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle IS_ICONIC = user32("IsIconic",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle ENUM_MONITORS = user32("EnumDisplayMonitors",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_LONG));
    private static final FunctionDescriptor MONITOR_CALLBACK = FunctionDescriptor.of(
            ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
            ValueLayout.JAVA_LONG);
    private static final MethodHandle IS_WINDOW = user32("IsWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle STYLE = user32("GetWindowLongPtrW",
            FunctionDescriptor.of(ValueLayout.JAVA_LONG, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle SHOW = user32("ShowWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle WINDOW_RECT = user32("GetWindowRect",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle CLIENT_RECT = user32("GetClientRect",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle CLIENT_TO_SCREEN = user32("ClientToScreen",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle MONITOR_FROM_WINDOW = user32("MonitorFromWindow",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle MONITOR_INFO = user32("GetMonitorInfoW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS));
    private static final MethodHandle SET_POSITION = user32("SetWindowPos",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT, ValueLayout.JAVA_INT));
    private static final MethodHandle FOREGROUND = user32("SetForegroundWindow",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));
    private static final MethodHandle POST = user32("PostMessageW",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_LONG, ValueLayout.JAVA_LONG));
    private static final MethodHandle FRAME_BOUNDS = LINKER.downcallHandle(
            DWMAPI.find("DwmGetWindowAttribute").orElseThrow(),
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS, ValueLayout.JAVA_INT));

    private GameWindows() {
    }

    private static MethodHandle user32(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(USER32.find(name).orElseThrow(), descriptor);
    }

    public static Optional<Long> find(long processId) {
        return aware(() -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment windowClass = arena.allocateFrom(WINDOW_CLASS,
                        java.nio.charset.StandardCharsets.UTF_16LE);
                MemorySegment owner = arena.allocate(ValueLayout.JAVA_INT);
                MemorySegment window = MemorySegment.NULL;
                while (true) {
                    window = (MemorySegment) FIND_WINDOW.invokeExact(MemorySegment.NULL, window,
                            windowClass, MemorySegment.NULL);
                    if (window.address() == 0L) {
                        return Optional.empty();
                    }
                    int thread = (int) WINDOW_PROCESS.invokeExact(window, owner);
                    if (thread != 0 && Integer.toUnsignedLong(owner.get(ValueLayout.JAVA_INT, 0))
                            == processId && (int) IS_VISIBLE.invokeExact(window) != 0) {
                        return Optional.of(window.address());
                    }
                }
            }
        });
    }

    public static boolean exists(long window) {
        return aware(() -> (int) IS_WINDOW.invokeExact(handle(window)) != 0);
    }

    public record Monitor(String device, boolean primary, int x, int y, int width, int height,
            int workX, int workY, int workWidth, int workHeight) {

        public int[] workArea() {
            return new int[] {workX, workY, workWidth, workHeight};
        }

        public Screen screen() {
            return new Screen(device, workX, workY, workWidth, workHeight);
        }

        public int number() {
            String digits = device.replaceAll("\\D", "");
            try {
                return digits.isEmpty() ? 0 : Integer.parseInt(digits);
            } catch (NumberFormatException tooLong) {
                return 0;
            }
        }
    }

    public static List<Monitor> monitors() {
        return aware(() -> {
            List<MemorySegment> handles = new ArrayList<>();
            MethodHandle collect = MethodHandles.lookup().findStatic(GameWindows.class,
                    "collectMonitor", MethodType.methodType(int.class, List.class,
                            MemorySegment.class, MemorySegment.class, MemorySegment.class,
                            long.class)).bindTo(handles);
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment callback = LINKER.upcallStub(collect, MONITOR_CALLBACK, arena);
                int listed = (int) ENUM_MONITORS.invokeExact(MemorySegment.NULL,
                        MemorySegment.NULL, callback, 0L);
                List<Monitor> found = new ArrayList<>();
                for (MemorySegment handle : handles) {
                    monitorInfo(handle, arena).ifPresent(found::add);
                }
                found.sort(Comparator.comparingInt(Monitor::number));
                return found;
            }
        });
    }

    private static int collectMonitor(List<MemorySegment> handles, MemorySegment monitor,
            MemorySegment context, MemorySegment bounds, long data) {
        handles.add(monitor);
        return 1;
    }

    private static Optional<Monitor> monitorInfo(MemorySegment monitor, Arena arena)
            throws Throwable {
        MemorySegment info = arena.allocate(MONITOR_INFO_BYTES);
        info.set(ValueLayout.JAVA_INT, 0, MONITOR_INFO_BYTES);
        if ((int) MONITOR_INFO.invokeExact(monitor, info) == 0) {
            return Optional.empty();
        }
        int left = info.get(ValueLayout.JAVA_INT, 4);
        int top = info.get(ValueLayout.JAVA_INT, 8);
        int right = info.get(ValueLayout.JAVA_INT, 12);
        int bottom = info.get(ValueLayout.JAVA_INT, 16);
        int workLeft = info.get(ValueLayout.JAVA_INT, 20);
        int workTop = info.get(ValueLayout.JAVA_INT, 24);
        int workRight = info.get(ValueLayout.JAVA_INT, 28);
        int workBottom = info.get(ValueLayout.JAVA_INT, 32);
        boolean primary = (info.get(ValueLayout.JAVA_INT, 36) & MONITORINFOF_PRIMARY) != 0;
        String device = info.getString(40, StandardCharsets.UTF_16LE);
        return Optional.of(new Monitor(device, primary, left, top, right - left, bottom - top,
                workLeft, workTop, workRight - workLeft, workBottom - workTop));
    }

    public static Monitor monitorOf(long window) {
        return aware(() -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment monitor = (MemorySegment) MONITOR_FROM_WINDOW.invokeExact(
                        handle(window), MONITOR_DEFAULTTONEAREST);
                return monitorInfo(monitor, arena).orElseThrow(() ->
                        new IllegalStateException("Windows did not say which screen it is on"));
            }
        });
    }

    public static boolean framed(long window) {
        return aware(() -> {
            long style = (long) STYLE.invokeExact(handle(window), GWL_STYLE);
            return (style & WS_CAPTION) == WS_CAPTION;
        });
    }

    public static boolean minimized(long window) {
        return aware(() -> (int) IS_ICONIC.invokeExact(handle(window)) != 0);
    }

    public static boolean maximized(long window) {
        return aware(() -> (int) IS_ZOOMED.invokeExact(handle(window)) != 0);
    }

    public static int[] visibleBounds(long window) {
        return aware(() -> {
            try (Arena arena = Arena.ofConfined()) {
                MemorySegment hwnd = handle(window);
                int[] outer = rect(WINDOW_RECT, hwnd, arena);
                int[] hidden = invisibleBorders(hwnd, outer, arena);
                int left = outer[0] + hidden[0];
                int top = outer[1] + hidden[1];
                int right = outer[2] - hidden[2];
                int bottom = outer[3] - hidden[3];
                return new int[] {left, top, right - left, bottom - top};
            }
        });
    }

    public static String place(long window, Placement placement) {
        return aware(() -> {
            MemorySegment hwnd = handle(window);
            long style = (long) STYLE.invokeExact(hwnd, GWL_STYLE);
            if ((style & WS_CAPTION) != WS_CAPTION) {
                return "it is in full screen or borderless mode";
            }
            if ((int) IS_ZOOMED.invokeExact(hwnd) != 0) {
                int shown = (int) SHOW.invokeExact(hwnd, SW_RESTORE);
            }
            try (Arena arena = Arena.ofConfined()) {
                int[] outer = rect(WINDOW_RECT, hwnd, arena);
                int[] client = rect(CLIENT_RECT, hwnd, arena);
                MemorySegment origin = arena.allocate(8);
                int converted = (int) CLIENT_TO_SCREEN.invokeExact(hwnd, origin);
                int clientLeft = origin.get(ValueLayout.JAVA_INT, 0);
                int clientTop = origin.get(ValueLayout.JAVA_INT, 4);
                int frameLeft = clientLeft - outer[0];
                int frameTop = clientTop - outer[1];
                int frameRight = outer[2] - (clientLeft + client[2]);
                int frameBottom = outer[3] - (clientTop + client[3]);
                int[] hidden = invisibleBorders(hwnd, outer, arena);
                int[] content = placement.grownBy(hidden[0], hidden[1], hidden[2], hidden[3])
                        .content(frameLeft, frameTop, frameRight, frameBottom);
                int moved = (int) SET_POSITION.invokeExact(hwnd, MemorySegment.NULL,
                        content[0] - frameLeft, content[1] - frameTop,
                        content[2] + frameLeft + frameRight, content[3] + frameTop + frameBottom,
                        SWP_NOZORDER | SWP_NOACTIVATE | SWP_NOOWNERZORDER);
                return moved == 0 ? "Windows refused to move it" : null;
            }
        });
    }

    public static void maximize(long window) {
        aware(() -> {
            MemorySegment hwnd = handle(window);
            long style = (long) STYLE.invokeExact(hwnd, GWL_STYLE);
            return (style & WS_CAPTION) == WS_CAPTION
                    ? (int) SHOW.invokeExact(hwnd, SW_MAXIMIZE)
                    : 0;
        });
    }

    public static void placeBelow(long window, long above) {
        aware(() -> (int) SET_POSITION.invokeExact(handle(window), handle(above), 0, 0, 0, 0,
                SWP_NOMOVE | SWP_NOSIZE | SWP_NOACTIVATE | SWP_NOOWNERZORDER));
    }

    public static boolean activate(long window) {
        return aware(() -> (int) FOREGROUND.invokeExact(handle(window)) != 0);
    }

    public static boolean bringToFront(long window) {
        return aware(() -> {
            MemorySegment hwnd = handle(window);
            if ((int) IS_ICONIC.invokeExact(hwnd) != 0) {
                int shown = (int) SHOW.invokeExact(hwnd, SW_RESTORE);
            }
            return (int) FOREGROUND.invokeExact(hwnd) != 0;
        });
    }

    public static void close(long window) {
        aware(() -> (int) POST.invokeExact(handle(window), WM_CLOSE, 0L, 0L));
    }

    private static int[] rect(MethodHandle read, MemorySegment hwnd, Arena arena)
            throws Throwable {
        MemorySegment rect = arena.allocate(RECT_BYTES);
        int ok = (int) read.invokeExact(hwnd, rect);
        int left = rect.get(ValueLayout.JAVA_INT, 0);
        int top = rect.get(ValueLayout.JAVA_INT, 4);
        int right = rect.get(ValueLayout.JAVA_INT, 8);
        int bottom = rect.get(ValueLayout.JAVA_INT, 12);
        return read == CLIENT_RECT
                ? new int[] {0, 0, right - left, bottom - top}
                : new int[] {left, top, right, bottom};
    }

    private static int[] invisibleBorders(MemorySegment hwnd, int[] outer, Arena arena)
            throws Throwable {
        MemorySegment bounds = arena.allocate(RECT_BYTES);
        int result = (int) FRAME_BOUNDS.invokeExact(hwnd, DWMWA_EXTENDED_FRAME_BOUNDS, bounds,
                RECT_BYTES);
        if (result != 0) {
            return new int[4];
        }
        return new int[] {
                plausible(bounds.get(ValueLayout.JAVA_INT, 0) - outer[0]),
                plausible(bounds.get(ValueLayout.JAVA_INT, 4) - outer[1]),
                plausible(outer[2] - bounds.get(ValueLayout.JAVA_INT, 8)),
                plausible(outer[3] - bounds.get(ValueLayout.JAVA_INT, 12))};
    }

    private static int plausible(int border) {
        return border < 0 || border > MOST_PLAUSIBLE_BORDER ? 0 : border;
    }

    private static MemorySegment handle(long window) {
        return MemorySegment.ofAddress(window);
    }

    private interface Win32<T> {
        T call() throws Throwable;
    }

    private static <T> T aware(Win32<T> body) {
        MemorySegment previous = MemorySegment.NULL;
        try {
            previous = (MemorySegment) SET_DPI_CONTEXT.invokeExact(
                    MemorySegment.ofAddress(PER_MONITOR_AWARE_V2));
            return body.call();
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new IllegalStateException(failure.getMessage(), failure);
        } finally {
            if (previous.address() != 0L) {
                try {
                    MemorySegment restored = (MemorySegment) SET_DPI_CONTEXT.invokeExact(previous);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}

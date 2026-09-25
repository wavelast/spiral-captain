package com.spiralcaptain.app.launch;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;

public final class ProcessPriority {

    private static final int PROCESS_SET_INFORMATION = 0x0200;
    private static final int PROCESS_QUERY_LIMITED_INFORMATION = 0x1000;
    private static final int NORMAL_PRIORITY_CLASS = 0x0020;
    private static final int BELOW_NORMAL_PRIORITY_CLASS = 0x4000;

    private static final Linker LINKER = Linker.nativeLinker();
    private static final SymbolLookup KERNEL32 =
            SymbolLookup.libraryLookup("kernel32", Arena.global());

    private static final MethodHandle OPEN_PROCESS = kernel32("OpenProcess",
            FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.JAVA_INT, ValueLayout.JAVA_INT,
                    ValueLayout.JAVA_INT));
    private static final MethodHandle SET_PRIORITY = kernel32("SetPriorityClass",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS, ValueLayout.JAVA_INT));
    private static final MethodHandle CLOSE_HANDLE = kernel32("CloseHandle",
            FunctionDescriptor.of(ValueLayout.JAVA_INT, ValueLayout.ADDRESS));

    private ProcessPriority() {
    }

    private static MethodHandle kernel32(String name, FunctionDescriptor descriptor) {
        return LINKER.downcallHandle(KERNEL32.find(name).orElseThrow(), descriptor);
    }

    public static boolean set(long processId, boolean belowNormal) {
        try {
            MemorySegment process = (MemorySegment) OPEN_PROCESS.invokeExact(
                    PROCESS_SET_INFORMATION | PROCESS_QUERY_LIMITED_INFORMATION, 0,
                    (int) processId);
            if (process.address() == 0L) {
                return false;
            }
            try {
                int changed = (int) SET_PRIORITY.invokeExact(process,
                        belowNormal ? BELOW_NORMAL_PRIORITY_CLASS : NORMAL_PRIORITY_CLASS);
                return changed != 0;
            } finally {
                int closed = (int) CLOSE_HANDLE.invokeExact(process);
            }
        } catch (Throwable failure) {
            return false;
        }
    }
}

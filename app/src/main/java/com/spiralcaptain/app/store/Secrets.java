package com.spiralcaptain.app.store;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

public final class Secrets {

    private static final int CRYPTPROTECT_UI_FORBIDDEN = 0x1;

    private static final MemoryLayout BLOB = MemoryLayout.structLayout(
            ValueLayout.JAVA_INT.withName("cbData"),
            MemoryLayout.paddingLayout(4),
            ValueLayout.ADDRESS.withName("pbData"));

    private static final long BLOB_LENGTH_OFFSET = 0L;
    private static final long BLOB_POINTER_OFFSET = 8L;

    private static final String PREFIX_DPAPI = "dpapi:";
    private static final String PREFIX_PLAIN = "plain:";

    private static MethodHandle protect;
    private static MethodHandle unprotect;
    private static MethodHandle localFree;
    private static String unavailableReason;

    static {
        try {
            Linker linker = Linker.nativeLinker();
            Arena arena = Arena.global();
            SymbolLookup crypt32 = SymbolLookup.libraryLookup("Crypt32.dll", arena);
            SymbolLookup kernel32 = SymbolLookup.libraryLookup("Kernel32.dll", arena);
            FunctionDescriptor cryptCall = FunctionDescriptor.of(ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.ADDRESS,
                    ValueLayout.JAVA_INT,
                    ValueLayout.ADDRESS);
            protect = linker.downcallHandle(
                    crypt32.findOrThrow("CryptProtectData"), cryptCall);
            unprotect = linker.downcallHandle(
                    crypt32.findOrThrow("CryptUnprotectData"), cryptCall);
            localFree = linker.downcallHandle(kernel32.findOrThrow("LocalFree"),
                    FunctionDescriptor.of(ValueLayout.ADDRESS, ValueLayout.ADDRESS));
        } catch (RuntimeException | Error unsupported) {
            unavailableReason = String.valueOf(unsupported.getMessage());
        }
    }

    private Secrets() {
    }

    public static boolean encryptionAvailable() {
        return protect != null && unprotect != null && localFree != null;
    }

    public static String unavailableReason() {
        return unavailableReason;
    }

    public static String digest(String password) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] hashed = md5.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hashed.length * 2);
            for (byte value : hashed) {
                hex.append("0123456789abcdef".charAt((value & 0xF0) >> 4));
                hex.append("0123456789abcdef".charAt(value & 0x0F));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException nsae) {
            throw new IllegalStateException("MD5 is not available in this JVM", nsae);
        }
    }

    public static String seal(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "";
        }
        byte[] raw = secret.getBytes(StandardCharsets.UTF_8);
        if (encryptionAvailable()) {
            try {
                return PREFIX_DPAPI + Base64.getEncoder().encodeToString(crypt(protect, raw));
            } catch (RuntimeException failure) {
                unavailableReason = String.valueOf(failure.getMessage());
            }
        }
        return PREFIX_PLAIN + Base64.getEncoder().encodeToString(raw);
    }

    public static String unseal(String stored) {
        if (stored == null || stored.isEmpty()) {
            return "";
        }
        if (stored.startsWith(PREFIX_PLAIN)) {
            return new String(Base64.getDecoder().decode(stored.substring(PREFIX_PLAIN.length())),
                    StandardCharsets.UTF_8);
        }
        if (!stored.startsWith(PREFIX_DPAPI)) {
            return stored;
        }
        if (!encryptionAvailable()) {
            return "";
        }
        byte[] sealed = Base64.getDecoder().decode(stored.substring(PREFIX_DPAPI.length()));
        try {
            return new String(crypt(unprotect, sealed), StandardCharsets.UTF_8);
        } catch (RuntimeException failure) {
            unavailableReason = String.valueOf(failure.getMessage());
            return "";
        }
    }

    private static byte[] crypt(MethodHandle call, byte[] input) {
        try (Arena arena = Arena.ofConfined()) {
            MemorySegment in = arena.allocate(BLOB);
            MemorySegment payload = arena.allocateFrom(ValueLayout.JAVA_BYTE, input);
            in.set(ValueLayout.JAVA_INT, BLOB_LENGTH_OFFSET, input.length);
            in.set(ValueLayout.ADDRESS, BLOB_POINTER_OFFSET, payload);

            MemorySegment out = arena.allocate(BLOB);
            int ok = (int) call.invokeExact(in, MemorySegment.NULL, MemorySegment.NULL,
                    MemorySegment.NULL, MemorySegment.NULL, CRYPTPROTECT_UI_FORBIDDEN, out);
            if (ok == 0) {
                throw new IllegalStateException("Windows refused the DPAPI call");
            }
            int length = out.get(ValueLayout.JAVA_INT, BLOB_LENGTH_OFFSET);
            MemorySegment pointer = out.get(ValueLayout.ADDRESS, BLOB_POINTER_OFFSET);
            try {
                return pointer.reinterpret(length).toArray(ValueLayout.JAVA_BYTE);
            } finally {
                MemorySegment freed = (MemorySegment) localFree.invokeExact(pointer);
                assert freed != null;
            }
        } catch (RuntimeException | Error passthrough) {
            throw passthrough;
        } catch (Throwable thrown) {
            throw new IllegalStateException("DPAPI call failed: " + thrown.getMessage(), thrown);
        }
    }
}

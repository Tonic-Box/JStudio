package com.tonic.ui.deobfuscation.model;

import com.tonic.parser.MethodEntry;
import lombok.Getter;
import lombok.Setter;

@Getter
public class DeobfuscationResult {

    private final String className;
    private final int constantPoolIndex;
    private final String originalValue;
    @Setter private String decryptedValue;
    @Setter private MethodEntry decryptorUsed;
    @Setter private boolean success;
    @Setter private String errorMessage;
    @Setter private long executionTimeMs;
    @Setter private boolean applied;

    public DeobfuscationResult(String className, int constantPoolIndex, String originalValue) {
        this.className = className;
        this.constantPoolIndex = constantPoolIndex;
        this.originalValue = originalValue;
        this.success = false;
        this.applied = false;
    }

    public static DeobfuscationResult success(String className, int cpIndex,
                                               String original, String decrypted,
                                               MethodEntry decryptor, long timeMs) {
        DeobfuscationResult result = new DeobfuscationResult(className, cpIndex, original);
        result.decryptedValue = decrypted;
        result.decryptorUsed = decryptor;
        result.success = true;
        result.executionTimeMs = timeMs;
        return result;
    }

    public static DeobfuscationResult failure(String className, int cpIndex,
                                               String original, String error) {
        DeobfuscationResult result = new DeobfuscationResult(className, cpIndex, original);
        result.success = false;
        result.errorMessage = error;
        return result;
    }

    public String getSimpleClassName() {
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
    }

    public String getDisplayOriginal() {
        return truncate(originalValue);
    }

    public String getDisplayDecrypted() {
        if (decryptedValue == null) return "-";
        return truncate(decryptedValue);
    }

    public String getStatusText() {
        if (applied) return "Applied";
        if (success) return "Decrypted";
        return "Failed";
    }

    private String truncate(String s) {
        if (s == null) return "";
        if (s.length() <= 30) return s;
        return s.substring(0, 30 - 3) + "...";
    }

    public String getLocation() {
        return getSimpleClassName() + ":" + constantPoolIndex;
    }

    @Override
    public String toString() {
        if (success) {
            return String.format("%s: \"%s\" → \"%s\"",
                getLocation(), getDisplayOriginal(), getDisplayDecrypted());
        } else {
            return String.format("%s: \"%s\" → ERROR: %s",
                getLocation(), getDisplayOriginal(), errorMessage);
        }
    }
}

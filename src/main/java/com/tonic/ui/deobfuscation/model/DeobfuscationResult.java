package com.tonic.ui.deobfuscation.model;

import com.tonic.parser.MethodEntry;

public class DeobfuscationResult {

    private final String className;
    private final int constantPoolIndex;
    private final String originalValue;
    private String decryptedValue;
    private MethodEntry decryptorUsed;
    private boolean success;
    private String errorMessage;
    private long executionTimeMs;
    private boolean applied;

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

    public String getClassName() {
        return className;
    }

    public String getSimpleClassName() {
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
    }

    public int getConstantPoolIndex() {
        return constantPoolIndex;
    }

    public String getOriginalValue() {
        return originalValue;
    }

    public String getDecryptedValue() {
        return decryptedValue;
    }

    public void setDecryptedValue(String decryptedValue) {
        this.decryptedValue = decryptedValue;
    }

    public MethodEntry getDecryptorUsed() {
        return decryptorUsed;
    }

    public void setDecryptorUsed(MethodEntry decryptorUsed) {
        this.decryptorUsed = decryptorUsed;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public boolean isApplied() {
        return applied;
    }

    public void setApplied(boolean applied) {
        this.applied = applied;
    }

    public String getDisplayOriginal() {
        return truncate(originalValue, 30);
    }

    public String getDisplayDecrypted() {
        if (decryptedValue == null) return "-";
        return truncate(decryptedValue, 30);
    }

    public String getStatusText() {
        if (applied) return "Applied";
        if (success) return "Decrypted";
        return "Failed";
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
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

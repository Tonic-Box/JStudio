package com.tonic.deobfuscation.model;

import com.tonic.parser.MethodEntry;
import lombok.Getter;
import lombok.Setter;

/** The outcome of decrypting one constant-pool string: original and decrypted text, the decryptor used, timing, error, and whether it was patched in. */
@Getter
public class DeobfuscationResult
{

    private final String className;
    private final int constantPoolIndex;
    private final String originalValue;
    @Setter
    private String decryptedValue;
    @Setter
    private MethodEntry decryptorUsed;
    @Setter
    private boolean success;
    @Setter
    private String errorMessage;
    @Setter
    private long executionTimeMs;
    @Setter
    private boolean applied;

    /**
     * Creates a result that is neither successful nor applied.
     *
     * @param className the internal name of the class holding the string
     * @param constantPoolIndex the string's constant-pool index
     * @param originalValue the encrypted string
     */
    public DeobfuscationResult(String className, int constantPoolIndex, String originalValue)
    {
        this.className = className;
        this.constantPoolIndex = constantPoolIndex;
        this.originalValue = originalValue;
        this.success = false;
        this.applied = false;
    }

    /**
     * Creates a successful result.
     *
     * @param className the internal name of the class holding the string
     * @param cpIndex the string's constant-pool index
     * @param original the encrypted string
     * @param decrypted the decrypted string
     * @param decryptor the method that decrypted it
     * @param timeMs how long decryption took, in milliseconds
     * @return the result
     */
    public static DeobfuscationResult success(String className, int cpIndex, String original, String decrypted, MethodEntry decryptor, long timeMs)
    {
        DeobfuscationResult result = new DeobfuscationResult(className, cpIndex, original);
        result.decryptedValue = decrypted;
        result.decryptorUsed = decryptor;
        result.success = true;
        result.executionTimeMs = timeMs;
        return result;
    }

    /**
     * Creates a failed result.
     *
     * @param className the internal name of the class holding the string
     * @param cpIndex the string's constant-pool index
     * @param original the encrypted string
     * @param error why decryption failed
     * @return the result
     */
    public static DeobfuscationResult failure(String className, int cpIndex, String original, String error)
    {
        DeobfuscationResult result = new DeobfuscationResult(className, cpIndex, original);
        result.success = false;
        result.errorMessage = error;
        return result;
    }

    /**
     * Strips the package from the class name.
     *
     * @return the class name after the last slash
     */
    public String getSimpleClassName()
    {
        int lastSlash = className.lastIndexOf('/');
        return lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
    }

    /**
     * Shortens the original string for display.
     *
     * @return the original, cut to 30 characters with an ellipsis, or empty when null
     */
    public String getDisplayOriginal()
    {
        return truncate(originalValue);
    }

    /**
     * Shortens the decrypted string for display.
     *
     * @return the decrypted text, cut to 30 characters with an ellipsis, or a dash when there is none
     */
    public String getDisplayDecrypted()
    {
        if (decryptedValue == null) return "-";
        return truncate(decryptedValue);
    }

    /**
     * Describes the result's state.
     *
     * @return Applied, Decrypted or Failed
     */
    public String getStatusText()
    {
        if (applied) return "Applied";
        if (success) return "Decrypted";
        return "Failed";
    }

    private String truncate(String s)
    {
        if (s == null) return "";
        if (s.length() <= 30) return s;
        return s.substring(0, 30 - 3) + "...";
    }

    /**
     * Formats where the string lives.
     *
     * @return the simple class name, a colon and the constant-pool index
     */
    public String getLocation()
    {
        return getSimpleClassName() + ":" + constantPoolIndex;
    }

    @Override
    public String toString()
    {
        if (success)
        {
            return String.format("%s: \"%s\" -> \"%s\"", getLocation(), getDisplayOriginal(), getDisplayDecrypted());
        }
        else
        {
            return String.format("%s: \"%s\" -> ERROR: %s", getLocation(), getDisplayOriginal(), errorMessage);
        }
    }
}

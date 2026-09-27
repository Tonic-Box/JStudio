package com.tonic.deobfuscation.model;

import com.tonic.deobfuscation.CallSites;
import com.tonic.parser.MethodEntry;
import lombok.Getter;
import lombok.Setter;

/** One row of string decryption: a suspicious constant-pool string, or a call to a decryptor with its recovered arguments, with the decrypted text, decryptor used, timing, error, and whether it was patched in. */
@Getter
public class DeobfuscationResult
{

    private final String className;
    private final int constantPoolIndex;
    private final String originalValue;
    private final CallSites.CallSite callSite;
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
     * Creates a row for a suspicious constant-pool string, neither decrypted nor applied.
     *
     * @param className the internal name of the class holding the string
     * @param constantPoolIndex the string's constant-pool index
     * @param originalValue the encrypted string
     */
    public DeobfuscationResult(String className, int constantPoolIndex, String originalValue)
    {
        this(className, constantPoolIndex, originalValue, null);
    }

    private DeobfuscationResult(String className, int constantPoolIndex, String originalValue, CallSites.CallSite callSite)
    {
        this.className = className;
        this.constantPoolIndex = constantPoolIndex;
        this.originalValue = originalValue;
        this.callSite = callSite;
    }

    /**
     * Creates a row for one call to a decryptor, neither decrypted nor applied.
     *
     * @param className the internal name of the class holding the call
     * @param callSite the call and its recovered arguments
     * @param constantPoolIndex the constant-pool index of the call's string argument when patching that string applies the result, or -1
     * @return the row, showing the call's arguments as its original value
     */
    public static DeobfuscationResult forCallSite(String className, CallSites.CallSite callSite, int constantPoolIndex)
    {
        return new DeobfuscationResult(className, constantPoolIndex, callSite.describeArguments(), callSite);
    }

    /**
     * Tells whether applying the row can patch a constant-pool string.
     *
     * @return true when the row decrypted a string held at a known constant-pool index
     */
    public boolean isApplicable()
    {
        return success && constantPoolIndex > 0;
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
     * Formats where the row's string or call is.
     *
     * @return for a call the simple class name, the calling method and the bytecode offset; otherwise the simple class name, a colon and the constant-pool index
     */
    public String getLocation()
    {
        if (callSite != null)
        {
            return getSimpleClassName() + "." + callSite.getCaller().getName() + "@" + callSite.getOffset();
        }
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

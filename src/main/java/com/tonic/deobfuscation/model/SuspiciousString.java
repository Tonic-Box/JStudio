package com.tonic.deobfuscation.model;

import com.tonic.parser.ClassFile;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** A constant-pool string that looks encrypted or encoded, with the reason and a suspicion score. */
@Getter
@RequiredArgsConstructor
public class SuspiciousString
{

    private final ClassFile classFile;
    private final int constantPoolIndex;
    private final String value;
    private final SuspicionReason reason;
    private final double suspicionScore;

    /**
     * Reads the holding class's name.
     *
     * @return the internal name of the class holding the string
     */
    public String getClassName()
    {
        return classFile.getClassName();
    }

    /**
     * Shortens the string for display.
     *
     * @return the value, cut to 40 characters with an ellipsis when longer
     */
    public String getDisplayValue()
    {
        if (value.length() > 40)
        {
            return value.substring(0, 37) + "...";
        }
        return value;
    }

    /** Why a string looks encrypted, with a readable description. */
    @Getter
    @RequiredArgsConstructor
    public enum SuspicionReason
    {
        HIGH_ENTROPY("High entropy - random-looking characters"),
        BASE64_PATTERN("Matches Base64 encoding pattern"),
        NON_PRINTABLE("Contains non-printable characters"),
        UNUSUAL_LENGTH("Unusual string length pattern"),
        HEX_PATTERN("Matches hexadecimal pattern");

        private final String description;
    }

    @Override
    public String toString()
    {
        return String.format("%s[%d]: \"%s\" (%s)", getClassName(), constantPoolIndex, getDisplayValue(), reason);
    }
}

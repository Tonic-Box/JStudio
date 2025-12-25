package com.tonic.ui.deobfuscation.model;

import com.tonic.parser.ClassFile;

public class SuspiciousString {

    private final ClassFile classFile;
    private final int constantPoolIndex;
    private final String value;
    private final SuspicionReason reason;
    private final double suspicionScore;

    public SuspiciousString(ClassFile classFile, int constantPoolIndex, String value,
                            SuspicionReason reason, double suspicionScore) {
        this.classFile = classFile;
        this.constantPoolIndex = constantPoolIndex;
        this.value = value;
        this.reason = reason;
        this.suspicionScore = suspicionScore;
    }

    public ClassFile getClassFile() {
        return classFile;
    }

    public int getConstantPoolIndex() {
        return constantPoolIndex;
    }

    public String getValue() {
        return value;
    }

    public SuspicionReason getReason() {
        return reason;
    }

    public double getSuspicionScore() {
        return suspicionScore;
    }

    public String getClassName() {
        return classFile.getClassName();
    }

    public String getDisplayValue() {
        if (value.length() > 40) {
            return value.substring(0, 37) + "...";
        }
        return value;
    }

    public enum SuspicionReason {
        HIGH_ENTROPY("High entropy - random-looking characters"),
        BASE64_PATTERN("Matches Base64 encoding pattern"),
        NON_PRINTABLE("Contains non-printable characters"),
        UNUSUAL_LENGTH("Unusual string length pattern"),
        HEX_PATTERN("Matches hexadecimal pattern");

        private final String description;

        SuspicionReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    @Override
    public String toString() {
        return String.format("%s[%d]: \"%s\" (%s)",
            getClassName(), constantPoolIndex, getDisplayValue(), reason);
    }
}

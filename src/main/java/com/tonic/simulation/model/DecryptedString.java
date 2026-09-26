package com.tonic.simulation.model;

import com.tonic.analysis.ssa.ir.InvokeInstruction;
import lombok.Getter;

/** An informational finding for a string that a call produced as a constant during simulation. */
@Getter
public class DecryptedString extends SimulationFinding
{

    private final String decryptedValue;
    private final String encryptedInput;
    private final String decryptionMethod;
    private final int blockId;

    /**
     * Creates a decrypted-string finding.
     *
     * @param className the owning class's internal name
     * @param methodName the method's name
     * @param methodDesc the method's descriptor
     * @param invokeInstr the call that returned the string, or null
     * @param decryptedValue the string it returned
     * @param encryptedInput the call's arguments rendered as text
     * @param decryptionMethod the called method as SimpleOwner.name
     */
    public DecryptedString(String className, String methodName, String methodDesc, InvokeInstruction invokeInstr, String decryptedValue, String encryptedInput, String decryptionMethod)
    {
        super(className, methodName, methodDesc, FindingType.DECRYPTED_STRING, Severity.INFO, invokeInstr != null && invokeInstr.getBlock() != null ? invokeInstr.getBlock().getBytecodeOffset() : -1);
        this.decryptedValue = decryptedValue;
        this.encryptedInput = encryptedInput;
        this.decryptionMethod = decryptionMethod;
        this.blockId = invokeInstr != null && invokeInstr.getBlock() != null
                ? invokeInstr.getBlock().getId() : -1;
    }

    @Override
    public String getTitle()
    {
        String truncated = decryptedValue;
        if (truncated != null && truncated.length() > 40)
        {
            truncated = truncated.substring(0, 37) + "...";
        }
        return "Decrypted String: \"" + truncated + "\"";
    }

    @Override
    public String getDescription()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("A string value was decrypted during simulation.\n\n");
        sb.append("Decrypted Value:\n  \"").append(decryptedValue).append("\"\n\n");
        if (encryptedInput != null && !encryptedInput.isEmpty())
        {
            sb.append("Encrypted Input:\n  ").append(encryptedInput).append("\n\n");
        }
        sb.append("Decryption Method:\n  ").append(decryptionMethod).append("\n");
        return sb.toString();
    }

    @Override
    public String getRecommendation()
    {
        return "This string was decoded/decrypted during simulation. " +
                "Consider replacing the encrypted reference with the plaintext value for analysis clarity.";
    }

    @Override
    public String toString()
    {
        return "DecryptedString[value=\"" + decryptedValue +
                "\", method=" + decryptionMethod + "]";
    }
}

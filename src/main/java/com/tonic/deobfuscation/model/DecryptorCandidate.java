package com.tonic.deobfuscation.model;

import com.tonic.parser.ClassFile;
import com.tonic.parser.MethodEntry;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

/** A method suspected of decrypting strings, with its signature type, a confidence from 0 to 1, and the indicators behind it. */
@Getter
public class DecryptorCandidate
{

    private final ClassFile classFile;
    private final MethodEntry method;
    private final DecryptorType type;
    private final double confidence;
    private final List<String> indicators;

    /**
     * Creates a candidate with no indicators.
     *
     * @param classFile the class declaring the method
     * @param method the suspected decryptor
     * @param type its signature pattern
     * @param confidence how likely it is a decryptor, from 0 to 1
     */
    public DecryptorCandidate(ClassFile classFile, MethodEntry method, DecryptorType type, double confidence)
    {
        this.classFile = classFile;
        this.method = method;
        this.type = type;
        this.confidence = confidence;
        this.indicators = new ArrayList<>();
    }

    /**
     * Records one reason the method looks like a decryptor.
     *
     * @param indicator a short human-readable reason
     */
    public void addIndicator(String indicator)
    {
        indicators.add(indicator);
    }

    /**
     * Reads the declaring class's name.
     *
     * @return the internal name of the declaring class
     */
    public String getClassName()
    {
        return classFile.getClassName();
    }

    /**
     * Reads the method's name.
     *
     * @return the method name
     */
    public String getMethodName()
    {
        return method.getName();
    }

    /**
     * Formats the method as its simple class name, a dot, the method name and empty parentheses.
     *
     * @return the short label, for example Foo.a()
     */
    public String getSimpleSignature()
    {
        String className = getClassName();
        int lastSlash = className.lastIndexOf('/');
        String simpleName = lastSlash >= 0 ? className.substring(lastSlash + 1) : className;
        return simpleName + "." + getMethodName() + "()";
    }

    /** A decryptor signature pattern, with a readable description and the method descriptor it matches. */
    @Getter
    public enum DecryptorType
    {
        STRING_TO_STRING("String -> String", "(Ljava/lang/String;)Ljava/lang/String;"),
        STRING_INT_TO_STRING("String, int -> String", "(Ljava/lang/String;I)Ljava/lang/String;"),
        INT_TO_STRING("int -> String (index-based)", "(I)Ljava/lang/String;"),
        BYTES_TO_STRING("byte[] -> String", "([B)Ljava/lang/String;"),
        STRING_TO_BYTES("String -> byte[]", "(Ljava/lang/String;)[B"),
        CHAR_ARRAY_TO_STRING("char[] -> String", "([C)Ljava/lang/String;"),
        UNKNOWN("Unknown pattern", null);

        private final String description;
        private final String expectedDescriptor;

        DecryptorType(String description, String expectedDescriptor)
        {
            this.description = description;
            this.expectedDescriptor = expectedDescriptor;
        }

        /**
         * Finds the pattern whose descriptor matches exactly.
         *
         * @param descriptor a method descriptor
         * @return the matching type, or UNKNOWN when none matches
         */
        public static DecryptorType fromDescriptor(String descriptor)
        {
            for (DecryptorType type : values())
            {
                if (type.expectedDescriptor != null && type.expectedDescriptor.equals(descriptor))
                {
                    return type;
                }
            }
            return UNKNOWN;
        }
    }

    @Override
    public String toString()
    {
        return String.format("%s [%s] (%.0f%% confidence)", getSimpleSignature(), type.getDescription(), confidence * 100);
    }
}

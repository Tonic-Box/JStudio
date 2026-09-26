package com.tonic.script.engine;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a JStudio transform script with metadata.
 */
@Getter
@Setter
public class Script
{

    /** Which representation a script transforms: the decompiled AST, the SSA IR, or both. */
    public enum Mode
    {
        AST,
        IR,
        BOTH
    }

    private String name;
    private String description;
    private Mode mode;
    private String version;
    private String author;
    private String content;
    private boolean builtIn;

    /** Creates an empty, untitled AST script at version 1.0. */
    public Script()
    {
        this.name = "Untitled";
        this.description = "";
        this.mode = Mode.AST;
        this.version = "1.0";
        this.author = "";
        this.content = "";
        this.builtIn = false;
    }

    /**
     * Creates a script with the given name, mode and source and default metadata.
     *
     * @param name the display name
     * @param mode which representation it transforms
     * @param content the script source
     */
    public Script(String name, Mode mode, String content)
    {
        this();
        this.name = name;
        this.mode = mode;
        this.content = content;
    }

    /**
     * Reads the mode from the first line comment of the form // @mode: ast, ir or both.
     *
     * @param content the script source, or null
     * @return the declared mode, or AST when there is none or it is not recognized
     */
    public static Mode parseModeFromContent(String content)
    {
        if (content == null) return Mode.AST;

        for (String line : content.split("\n"))
        {
            line = line.trim();
            if (line.startsWith("// @mode:") || line.startsWith("//@mode:"))
            {
                String modeStr = line.substring(line.indexOf(':') + 1).trim().toLowerCase();
                switch (modeStr)
                {
                    case "ir":
                        return Mode.IR;
                    case "both":
                        return Mode.BOTH;
                    default:
                        return Mode.AST;
                }
            }
        }
        return Mode.AST;
    }

    /**
     * Reads the name from the first line comment of the form // @name: followed by the name.
     *
     * @param content the script source, or null
     * @return the declared name, or Untitled when there is none
     */
    public static String parseNameFromContent(String content)
    {
        if (content == null) return "Untitled";

        for (String line : content.split("\n"))
        {
            line = line.trim();
            if (line.startsWith("// @name:") || line.startsWith("//@name:"))
            {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return "Untitled";
    }

    @Override
    public String toString()
    {
        return name;
    }
}

package com.tonic.script.engine;

import lombok.Getter;
import lombok.Setter;

/**
 * Represents a JStudio transform script with metadata.
 */
@Getter
@Setter
public class Script {

    public enum Mode {
        AST,    // Target decompiled source AST
        IR,     // Target SSA IR bytecode
        BOTH    // Run on both (AST first, then IR)
    }

    private String name;
    private String description;
    private Mode mode;
    private String version;
    private String author;
    private String content;
    private boolean builtIn;

    public Script() {
        this.name = "Untitled";
        this.description = "";
        this.mode = Mode.AST;
        this.version = "1.0";
        this.author = "";
        this.content = "";
        this.builtIn = false;
    }

    public Script(String name, Mode mode, String content) {
        this();
        this.name = name;
        this.mode = mode;
        this.content = content;
    }

    /**
     * Parses mode from script content annotations.
     * Looks for: // @mode: ast|ir|both
     */
    public static Mode parseModeFromContent(String content) {
        if (content == null) return Mode.AST;

        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.startsWith("// @mode:") || line.startsWith("//@mode:")) {
                String modeStr = line.substring(line.indexOf(':') + 1).trim().toLowerCase();
                switch (modeStr) {
                    case "ir": return Mode.IR;
                    case "both": return Mode.BOTH;
                    default: return Mode.AST;
                }
            }
        }
        return Mode.AST;
    }

    /**
     * Parses name from script content annotations.
     * Looks for: // @name: Script Name
     */
    public static String parseNameFromContent(String content) {
        if (content == null) return "Untitled";

        for (String line : content.split("\n")) {
            line = line.trim();
            if (line.startsWith("// @name:") || line.startsWith("//@name:")) {
                return line.substring(line.indexOf(':') + 1).trim();
            }
        }
        return "Untitled";
    }

    @Override
    public String toString() {
        return name;
    }
}

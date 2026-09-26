package com.tonic.ui.editor.dual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** A per-method, two-way map between bytecode view display lines and bytecode offsets, parsed from the rendered text; the one place that depends on the disassembly text format. */
public final class BytecodeLineIndex
{

    private static final Pattern INSTRUCTION = Pattern.compile("^\\s+(\\d+):\\s");

    private static final class MethodBlock
    {
        final String name;
        final String desc;
        final int headerLine;
        int endLine;
        final NavigableMap<Integer, Integer> pcToLine = new TreeMap<>();
        final Map<Integer, Integer> lineToPc = new HashMap<>();

        MethodBlock(String name, String desc, int headerLine)
        {
            this.name = name;
            this.desc = desc;
            this.headerLine = headerLine;
            this.endLine = headerLine;
        }
    }

    private final NavigableMap<Integer, MethodBlock> blockByHeaderLine = new TreeMap<>();
    private final Map<String, MethodBlock> blockByKey = new HashMap<>();

    private BytecodeLineIndex()
    {
    }

    /**
     * Parses rendered bytecode view text into an index.
     *
     * @param bytecodeText the bytecode view's full text; null or empty gives an empty index
     * @return the index
     */
    public static BytecodeLineIndex parse(String bytecodeText)
    {
        BytecodeLineIndex index = new BytecodeLineIndex();
        if (bytecodeText == null || bytecodeText.isEmpty())
        {
            return index;
        }
        String[] lines = bytecodeText.split("\n", -1);
        MethodBlock current = null;
        for (int line = 0; line < lines.length; line++)
        {
            String text = lines[line];
            String headerToken = methodHeaderToken(text);
            if (headerToken != null)
            {
                int paren = headerToken.indexOf('(');
                String name = headerToken.substring(0, paren);
                String desc = headerToken.substring(paren);
                current = new MethodBlock(name, desc, line);
                index.blockByHeaderLine.put(line, current);
                index.blockByKey.put(name + desc, current);
                continue;
            }
            if (current == null)
            {
                continue;
            }
            Matcher m = INSTRUCTION.matcher(text);
            if (m.lookingAt())
            {
                int pc = Integer.parseInt(m.group(1));
                current.pcToLine.putIfAbsent(pc, line);
                current.lineToPc.put(line, pc);
                current.endLine = line;
            }
            else if (!text.isEmpty())
            {
                current.endLine = line;
            }
        }
        return index;
    }

    private static String methodHeaderToken(String line)
    {
        if (!line.startsWith("//"))
        {
            return null;
        }
        int lastSpace = line.lastIndexOf(' ');
        if (lastSpace < 0)
        {
            return null;
        }
        String token = line.substring(lastSpace + 1).trim();
        return token.indexOf('(') > 0 ? token : null;
    }

    /**
     * Finds the instruction shown on a display line.
     *
     * @param displayLine the 0-based display line
     * @return the instruction's location, or null when the line is a header, comment, blank, or outside any method
     */
    public BcLocation locationAtLine(int displayLine)
    {
        Map.Entry<Integer, MethodBlock> entry = blockByHeaderLine.floorEntry(displayLine);
        if (entry == null)
        {
            return null;
        }
        MethodBlock block = entry.getValue();
        if (displayLine > block.endLine)
        {
            return null;
        }
        Integer pc = block.lineToPc.get(displayLine);
        if (pc == null)
        {
            return null;
        }
        return new BcLocation(block.name, block.desc, pc);
    }

    /**
     * Finds the display lines of a method's instructions within an offset range.
     *
     * @param methodKey the method's name followed by its descriptor
     * @param pcLo the lowest offset, inclusive
     * @param pcHi the highest offset, inclusive
     * @return the 0-based display lines in ascending offset order; empty when the method or range has no mapped instructions
     */
    public List<Integer> displayLinesForPcRange(String methodKey, int pcLo, int pcHi)
    {
        MethodBlock block = blockByKey.get(methodKey);
        if (block == null || pcLo > pcHi)
        {
            return new ArrayList<>();
        }
        return new ArrayList<>(block.pcToLine.subMap(pcLo, true, pcHi, true).values());
    }
}

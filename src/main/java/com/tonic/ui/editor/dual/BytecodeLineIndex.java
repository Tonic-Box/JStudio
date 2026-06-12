package com.tonic.ui.editor.dual;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the rendered bytecode text produced by {@code BytecodeView}/{@code BytecodeFormatter} into a
 * bidirectional map between display lines and bytecode offsets, grouped per method. This is the only
 * place that depends on the disassembly text format, so the dual view's cross-pane linking has a
 * single, testable point of coupling to that format.
 *
 * <p>Line shapes (display lines are 0-based):
 * <ul>
 *   <li><b>Method header</b> — emitted by {@code BytecodeView} at column 0 as {@code // <flags> name+desc};
 *       recognized by a leading {@code //} with no indentation whose last token contains {@code '('}.
 *       Every verbose disassembly comment ({@code // line}, {@code // frame}, {@code // signature},
 *       exception table, etc.) is indented by {@code BytecodeFormatter}, so it never matches.</li>
 *   <li><b>Instruction</b> — {@code <indent><offset>: <mnemonic> ...}; recognized by leading whitespace,
 *       decimal offset, immediate colon. Switch continuation lines ({@code case[..] => ...}) and verbose
 *       comments do not match.</li>
 * </ul>
 */
public final class BytecodeLineIndex {

    private static final Pattern INSTRUCTION = Pattern.compile("^\\s+(\\d+):\\s");

    private static final class MethodBlock {
        final String name;
        final String desc;
        final int headerLine;
        int endLine;
        final NavigableMap<Integer, Integer> pcToLine = new TreeMap<>();
        final Map<Integer, Integer> lineToPc = new HashMap<>();

        MethodBlock(String name, String desc, int headerLine) {
            this.name = name;
            this.desc = desc;
            this.headerLine = headerLine;
            this.endLine = headerLine;
        }
    }

    private final NavigableMap<Integer, MethodBlock> blockByHeaderLine = new TreeMap<>();
    private final Map<String, MethodBlock> blockByKey = new HashMap<>();

    private BytecodeLineIndex() {
    }

    public static BytecodeLineIndex parse(String bytecodeText) {
        BytecodeLineIndex index = new BytecodeLineIndex();
        if (bytecodeText == null || bytecodeText.isEmpty()) {
            return index;
        }
        String[] lines = bytecodeText.split("\n", -1);
        MethodBlock current = null;
        for (int line = 0; line < lines.length; line++) {
            String text = lines[line];
            String headerToken = methodHeaderToken(text);
            if (headerToken != null) {
                int paren = headerToken.indexOf('(');
                String name = headerToken.substring(0, paren);
                String desc = headerToken.substring(paren);
                current = new MethodBlock(name, desc, line);
                index.blockByHeaderLine.put(line, current);
                index.blockByKey.put(name + desc, current);
                continue;
            }
            if (current == null) {
                continue;
            }
            Matcher m = INSTRUCTION.matcher(text);
            if (m.lookingAt()) {
                int pc = Integer.parseInt(m.group(1));
                current.pcToLine.putIfAbsent(pc, line);
                current.lineToPc.put(line, pc);
                current.endLine = line;
            } else if (!text.isEmpty()) {
                current.endLine = line;
            }
        }
        return index;
    }

    /**
     * The token holding {@code name+desc} when {@code line} is a {@code BytecodeView} method header
     * (column-0 {@code //}, last token contains {@code '('}), otherwise null.
     */
    private static String methodHeaderToken(String line) {
        if (!line.startsWith("//")) {
            return null;
        }
        int lastSpace = line.lastIndexOf(' ');
        if (lastSpace < 0) {
            return null;
        }
        String token = line.substring(lastSpace + 1).trim();
        return token.indexOf('(') > 0 ? token : null;
    }

    /**
     * The instruction location at a 0-based display line, or null when the line is not an instruction
     * line (header, comment, blank, or outside any method).
     */
    public BcLocation locationAtLine(int displayLine) {
        Map.Entry<Integer, MethodBlock> entry = blockByHeaderLine.floorEntry(displayLine);
        if (entry == null) {
            return null;
        }
        MethodBlock block = entry.getValue();
        if (displayLine > block.endLine) {
            return null;
        }
        Integer pc = block.lineToPc.get(displayLine);
        if (pc == null) {
            return null;
        }
        return new BcLocation(block.name, block.desc, pc);
    }

    /**
     * The 0-based display lines of the instructions whose offset falls in {@code [pcLo, pcHi]} for the
     * method keyed by {@code name+desc}, in ascending offset order. Empty when the method or range has
     * no mapped instructions.
     */
    public List<Integer> displayLinesForPcRange(String methodKey, int pcLo, int pcHi) {
        MethodBlock block = blockByKey.get(methodKey);
        if (block == null || pcLo > pcHi) {
            return new ArrayList<>();
        }
        return new ArrayList<>(block.pcToLine.subMap(pcLo, true, pcHi, true).values());
    }
}
